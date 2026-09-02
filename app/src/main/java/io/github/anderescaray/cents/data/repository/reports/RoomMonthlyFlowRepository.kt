package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.MonthlyFlow
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.repository.reports.MonthlyFlowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The columns, aggregated by SQLite.
 *
 * The one pass Kotlin makes is over the *months* of the range, filling the empty ones with
 * zeros — a gap in a bar chart reads as missing data, and a zero reads as a quiet month.
 * That walk is bounded by the length of the period, not by the number of movements.
 */
@Singleton
class RoomMonthlyFlowRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
    ) : MonthlyFlowRepository {
        override fun observeMonthlyFlow(
            period: ReportPeriod,
            currency: String,
        ): Flow<List<MonthlyFlow>> =
            transactionDao
                .observeMonthlyTotals(period.from, period.to)
                .map { rows ->
                    val byMonth = rows.associateBy { YearMonth.parse(it.month) }
                    monthsIn(period).map { month ->
                        val totals = byMonth[month]
                        MonthlyFlow(
                            month = month,
                            summary =
                                PeriodSummary(
                                    income = Money(totals?.incomeMinor ?: 0, currency),
                                    expenses = Money(totals?.expenseMinor ?: 0, currency),
                                ),
                        )
                    }
                }
    }

/**
 * Every calendar month the period touches, in order and including partial ones at either
 * end. A range of a single day is one month, not zero.
 */
private fun monthsIn(period: ReportPeriod): List<YearMonth> {
    val first = YearMonth.from(period.from)
    val last = YearMonth.from(period.to)
    if (last < first) return emptyList()
    return generateSequence(first) { it.plusMonths(1) }
        .takeWhile { it <= last }
        .toList()
}

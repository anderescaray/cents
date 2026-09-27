package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.FlowBucket
import io.github.anderescaray.cents.domain.model.FlowShape
import io.github.anderescaray.cents.domain.model.IncomeAndExpenses
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.bucketsFor
import io.github.anderescaray.cents.domain.repository.reports.MonthlyFlowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The columns, aggregated by SQLite month by month.
 *
 * A quarter or a year is then the sum of its months, in Kotlin. That walk is bounded by the
 * months on the chart, at most 24 years' worth, never by the number of movements, so the
 * aggregation that matters still happens in `GROUP BY` (`CLAUDE.md` §5) and the query stays
 * the one this view has always run.
 */
@Singleton
class RoomMonthlyFlowRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
    ) : MonthlyFlowRepository {
        override fun observeFlow(
            scope: ReportScope,
            shape: FlowShape,
        ): Flow<IncomeAndExpenses> {
            val ranges = bucketsFor(scope.period, shape)
            return transactionDao
                .observeMonthlyTotals(ranges.first().second.from, ranges.last().second.to, scope.accountId)
                .map { rows ->
                    val months = rows.map { YearMonth.parse(it.month) to it }
                    IncomeAndExpenses(
                        scope = scope,
                        shape = shape,
                        buckets =
                            ranges.map { (whole, counted) ->
                                val inside =
                                    months
                                        .filter { (month, _) -> month.atDay(1) in counted.from..counted.to }
                                        .map { it.second }
                                FlowBucket(
                                    whole = whole,
                                    counted = counted,
                                    summary =
                                        PeriodSummary(
                                            income = Money(inside.sumOf { it.incomeMinor }, scope.currency),
                                            expenses = Money(inside.sumOf { it.expenseMinor }, scope.currency),
                                        ),
                                )
                            },
                    )
                }
        }
    }

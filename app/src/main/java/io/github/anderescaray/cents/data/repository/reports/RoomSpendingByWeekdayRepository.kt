package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.SpendingByWeekday
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.WeekdaySpending
import io.github.anderescaray.cents.domain.repository.reports.SpendingByWeekdayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The weekday bars, aggregated by SQLite.
 *
 * The one pass Kotlin makes is over the seven days of the week, turning the rows SQL grouped
 * into a full Monday-to-Sunday list with the quiet days as zeros. That is a fixed seven steps,
 * not one per movement.
 */
@Singleton
class RoomSpendingByWeekdayRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
    ) : SpendingByWeekdayRepository {
        override fun observeSpendingByWeekday(scope: ReportScope): Flow<SpendingByWeekday> =
            transactionDao
                .observeTotalsByWeekday(
                    TransactionKind.EXPENSE,
                    scope.period.from,
                    scope.period.to,
                    scope.accountId,
                ).map { rows ->
                    val byDay = rows.associateBy { DayOfWeek.of(it.weekday) }
                    SpendingByWeekday(
                        scope = scope,
                        days =
                            DayOfWeek.entries.map { day ->
                                val row = byDay[day]
                                WeekdaySpending(
                                    weekday = day,
                                    total = Money(row?.totalMinor ?: 0, scope.currency),
                                    transactionCount = row?.transactionCount ?: 0,
                                )
                            },
                    )
                }
    }

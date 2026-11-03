package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.BalanceOverTime
import io.github.anderescaray.cents.domain.model.BalancePoint
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.repository.reports.BalanceOverTimeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The balance line, aggregated by SQLite.
 *
 * Two queries and one running total. SQL answers *what each day did* and *what there was
 * before the period*; Kotlin walks the days of the period once, carrying the total forward
 * and repeating it across the days nothing happened. That walk is bounded by the length of
 * the period — a month is 31 steps and a year 365 — rather than by how many movements the
 * app has ever recorded, which is the rule the other two report repositories follow too
 * (`CLAUDE.md` §5).
 *
 * The two flows are combined rather than collected apart on purpose: the opening balance and
 * the days inside the period are halves of one line, and one write should produce one pass
 * through here rather than two halves updating at their own pace. It does not make the pair
 * atomic, and this comment used to say that it did — `combine` pairs the newest value of one
 * flow with the last value of the other, so a single frame can still show a line that starts
 * in the wrong place before the second emission lands (`docs/known-limitations.md`).
 */
@Singleton
class RoomBalanceOverTimeRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
    ) : BalanceOverTimeRepository {
        override fun observeBalanceOverTime(scope: ReportScope): Flow<BalanceOverTime> {
            val period = scope.period
            return combine(
                transactionDao.observeBalanceBefore(period.from, scope.accountId),
                transactionDao.observeDailyNet(period.from, period.to, scope.accountId),
            ) { openingMinor, days ->
                val netByDate = days.associate { it.date to it.netMinor }
                var running = openingMinor
                val points =
                    period.days().map { day ->
                        running += netByDate[day] ?: 0L
                        BalancePoint(day, Money(running, scope.currency))
                    }
                BalanceOverTime(
                    period = period,
                    opening = Money(openingMinor, scope.currency),
                    points = points,
                    // Days, not movements: what this separates is "nothing happened" from
                    // "it happened and cancelled out", and both of those are days.
                    activeDays = days.count { it.movementCount > 0 },
                    oneAccount = scope.accountId != null,
                )
            }
        }

        override fun observeFirstDate(accountId: Long?): Flow<LocalDate?> =
            transactionDao.observeFirstBalanceDate(accountId)
    }

/**
 * Every day of the period, in order and both ends included.
 *
 * A range whose end is before its start is no days rather than an exception — the view model
 * straightens backwards ranges before they get here, and a chart is not the place to discover
 * that it failed to.
 */
private fun ReportPeriod.days(): List<LocalDate> {
    if (to < from) return emptyList()
    return generateSequence(from) { it.plusDays(1) }
        .takeWhile { it <= to }
        .toList()
}

package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.dao.PeriodTotals
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.ComparedRanges
import io.github.anderescaray.cents.domain.model.ComparisonSegment
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodComparison
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.comparedWith
import io.github.anderescaray.cents.domain.repository.reports.PeriodComparisonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two totals, both summed by SQLite.
 *
 * Kotlin does no adding up here at all: each period is one `SUM` over an indexed date range,
 * and the differences are two subtractions between numbers SQL already produced. The work
 * does not grow with how many movements the app has ever recorded (`CLAUDE.md` §5).
 *
 * The two flows are combined rather than collected apart, so that one write produces one
 * pass through here rather than two independent screens updating at their own pace. It is
 * worth being exact about what that does and does not buy: `combine` pairs the newest value
 * of one flow with the *last* value of the other, so a movement moved from one period to the
 * other does briefly render as counted twice or not at all before the second emission lands.
 * Making that impossible needs one query returning both totals, which costs the index this
 * repository is otherwise able to use — recorded in `docs/known-limitations.md` rather than
 * traded away for a frame.
 *
 * **It picks the query rather than passing a nullable account into one.** The other three
 * reports take the account as `:accountId IS NULL OR ...`, which is not sargable and gives up
 * `index_transactions_accountId_date` (`docs/known-limitations.md`). Here two totals queries
 * already existed — one for every account, one for a given account — so the narrowed case gets
 * the index for free, and this is what the split recorded in that gap would look like
 * everywhere else.
 */
@Singleton
class RoomPeriodComparisonRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
        private val clock: Clock,
    ) : PeriodComparisonRepository {
        override fun observePeriodComparison(
            scope: ReportScope,
            segment: ComparisonSegment,
        ): Flow<PeriodComparison> {
            // The clock is injected rather than read here, so a test can stand in the middle
            // of a month and check what this does about it.
            val ranges: ComparedRanges = scope.period.comparedWith(LocalDate.now(clock), segment)
            return combine(
                totalsFor(ranges.current, scope.accountId),
                totalsFor(ranges.previous, scope.accountId),
            ) { current, before ->
                PeriodComparison(
                    scope = scope,
                    ranges = ranges,
                    current = current.toSummary(scope.currency),
                    before = before.toSummary(scope.currency),
                    segment = segment,
                )
            }
        }

        private fun totalsFor(
            period: ReportPeriod,
            accountId: Long?,
        ): Flow<PeriodTotals> =
            if (accountId == null) {
                transactionDao.observeTotalsBetween(period.from, period.to)
            } else {
                transactionDao.observeAccountTotalsBetween(accountId, period.from, period.to)
            }
    }

private fun PeriodTotals.toSummary(currency: String) =
    PeriodSummary(
        income = Money(incomeMinor, currency),
        expenses = Money(expenseMinor, currency),
    )

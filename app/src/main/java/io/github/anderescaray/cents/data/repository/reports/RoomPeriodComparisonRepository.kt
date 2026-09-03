package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.dao.PeriodTotals
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodComparison
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.previous
import io.github.anderescaray.cents.domain.repository.reports.PeriodComparisonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two totals, both summed by SQLite.
 *
 * Kotlin does no adding up here at all: each period is one `SUM` over an indexed date range,
 * and the differences are two subtractions between numbers SQL already produced. The work
 * does not grow with how many movements the app has ever recorded (`CLAUDE.md` §5).
 *
 * The two flows are combined rather than collected apart, for the same reason the balance
 * line combines its halves: a frame where this period has updated and the one it is being
 * compared against has not is a comparison that is briefly a lie.
 *
 * **It picks the query rather than passing a nullable account into one.** The other three
 * reports take the account as `:accountId IS NULL OR ...`, which is not sargable and gives up
 * `index_transactions_accountId_date` (`PLAN.md` C4). Here two totals queries already existed
 * — one for every account, one for a given account — so the narrowed case gets the index for
 * free, and this is what the split recorded in that gap would look like everywhere else.
 */
@Singleton
class RoomPeriodComparisonRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
    ) : PeriodComparisonRepository {
        override fun observePeriodComparison(scope: ReportScope): Flow<PeriodComparison> {
            val previous = scope.period.previous()
            return combine(
                totalsFor(scope.period, scope.accountId),
                totalsFor(previous, scope.accountId),
            ) { current, before ->
                PeriodComparison(
                    scope = scope,
                    previous = previous,
                    current = current.toSummary(scope.currency),
                    before = before.toSummary(scope.currency),
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

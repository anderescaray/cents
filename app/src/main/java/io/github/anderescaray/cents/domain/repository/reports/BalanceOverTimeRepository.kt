package io.github.anderescaray.cents.domain.repository.reports

import io.github.anderescaray.cents.domain.model.BalanceOverTime
import io.github.anderescaray.cents.domain.model.ReportScope
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * How much money there was, day by day, across the period — and what there was before it
 * started, which is where the line begins. [BalanceOverTime] carries that argument.
 *
 * One interface per view on the Reports tab — see `SpendingByCategoryRepository` for the
 * shared rule, and `ui/feature/reports/ReportSection.kt` for the seam this is half of.
 */
interface BalanceOverTimeRepository {
    fun observeBalanceOverTime(scope: ReportScope): Flow<BalanceOverTime>

    /**
     * The day of the first movement or correction in the accounts the line follows (one account,
     * or cash and bank), where *all time* starts; null when there is none.
     */
    fun observeFirstDate(accountId: Long?): Flow<LocalDate?>
}

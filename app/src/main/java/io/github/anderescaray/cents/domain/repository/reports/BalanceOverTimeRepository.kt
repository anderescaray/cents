package io.github.anderescaray.cents.domain.repository.reports

import io.github.anderescaray.cents.domain.model.BalanceOverTime
import io.github.anderescaray.cents.domain.model.ReportPeriod
import kotlinx.coroutines.flow.Flow

/**
 * How much money there was, day by day, across the period — and what there was before it
 * started, which is where the line begins. [BalanceOverTime] carries that argument.
 *
 * One interface per view on the Reports tab — see `SpendingByCategoryRepository` for the
 * shared rule, and `ui/feature/reports/ReportSection.kt` for the seam this is half of.
 */
interface BalanceOverTimeRepository {
    fun observeBalanceOverTime(
        period: ReportPeriod,
        currency: String,
    ): Flow<BalanceOverTime>
}

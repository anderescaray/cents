package io.github.anderescaray.cents.domain.repository.reports

import io.github.anderescaray.cents.domain.model.MonthlyFlow
import io.github.anderescaray.cents.domain.model.ReportScope
import kotlinx.coroutines.flow.Flow

/**
 * Income against expenses, one entry per calendar month touched by the period, in order and
 * with empty months present as zeros rather than missing.
 *
 * One interface per view on the Reports tab — see `SpendingByCategoryRepository` for the
 * shared rule, and `ui/feature/reports/ReportSection.kt` for the seam this is half of.
 */
interface MonthlyFlowRepository {
    fun observeMonthlyFlow(scope: ReportScope): Flow<List<MonthlyFlow>>
}

package io.github.anderescaray.cents.domain.repository.reports

import io.github.anderescaray.cents.domain.model.FlowShape
import io.github.anderescaray.cents.domain.model.IncomeAndExpenses
import io.github.anderescaray.cents.domain.model.ReportScope
import kotlinx.coroutines.flow.Flow

/**
 * Income against expenses, one column per month, quarter or year, as [FlowShape] asks, in
 * order and with the quiet ones present as zeros rather than missing.
 *
 * One interface per view on the Reports tab — see `SpendingByCategoryRepository` for the
 * shared rule, and `ui/feature/reports/ReportSection.kt` for the seam this is half of.
 */
interface MonthlyFlowRepository {
    fun observeFlow(
        scope: ReportScope,
        shape: FlowShape,
    ): Flow<IncomeAndExpenses>
}

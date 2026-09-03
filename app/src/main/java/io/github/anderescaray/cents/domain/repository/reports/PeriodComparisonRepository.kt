package io.github.anderescaray.cents.domain.repository.reports

import io.github.anderescaray.cents.domain.model.PeriodComparison
import io.github.anderescaray.cents.domain.model.ReportScope
import kotlinx.coroutines.flow.Flow

/**
 * This period's income and expenses against the previous period's.
 *
 * One interface per view on the Reports tab — see `SpendingByCategoryRepository` for the
 * shared rule, and `ui/feature/reports/ReportSection.kt` for the seam this is half of.
 */
interface PeriodComparisonRepository {
    fun observePeriodComparison(scope: ReportScope): Flow<PeriodComparison>
}

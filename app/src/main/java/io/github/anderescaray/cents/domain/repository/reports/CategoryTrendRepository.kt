package io.github.anderescaray.cents.domain.repository.reports

import io.github.anderescaray.cents.domain.model.CategoryTrend
import io.github.anderescaray.cents.domain.model.ReportScope
import kotlinx.coroutines.flow.Flow

/**
 * One category, followed across the last several periods.
 *
 * One interface per view on the Reports tab — see `SpendingByCategoryRepository` for the
 * shared rule, and `ui/feature/reports/ReportSection.kt` for the seam this is half of.
 *
 * [categoryId] is the only place a report is asked for something the scope does not carry, and
 * it is deliberate: which category to watch is a choice made *inside* the view, not a way the
 * whole tab is narrowed. Null means "pick the one most is spent on", which is what the view
 * opens on.
 */
interface CategoryTrendRepository {
    fun observeCategoryTrend(
        scope: ReportScope,
        categoryId: Long?,
    ): Flow<CategoryTrend>
}

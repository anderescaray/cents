package io.github.anderescaray.cents.domain.repository.reports

import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.SpendingByWeekday
import kotlinx.coroutines.flow.Flow

/**
 * Spending grouped by day of the week.
 *
 * One interface per view on the Reports tab — see `SpendingByCategoryRepository` for the
 * shared rule, and `ui/feature/reports/ReportSection.kt` for the seam this is half of.
 */
interface SpendingByWeekdayRepository {
    fun observeSpendingByWeekday(scope: ReportScope): Flow<SpendingByWeekday>
}

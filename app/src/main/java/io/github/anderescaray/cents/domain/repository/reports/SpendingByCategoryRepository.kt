package io.github.anderescaray.cents.domain.repository.reports

import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.Flow

/**
 * Where the money went: one slice per category over [ReportPeriod], largest first, including
 * an unnamed slice for whatever was never categorized.
 *
 * One interface per view on the Reports tab, each in its own file in this package — see
 * `ui/feature/reports/ReportSection.kt` for why, and for what else a new view has to touch.
 * All of them share one rule: **the aggregation happens in SQL**. A chart summarises the
 * whole history, so anything that ends up looping over transactions in Kotlin gets slower
 * with every movement ever recorded (`CLAUDE.md` §5).
 */
interface SpendingByCategoryRepository {
    /**
     * [kind] is a parameter because the same shape answers "what did I spend on" and "where
     * did the money come from"; the first chart asks for expenses (D4).
     */
    fun observeSpendingByCategory(
        period: ReportPeriod,
        currency: String,
        kind: TransactionKind = TransactionKind.EXPENSE,
    ): Flow<SpendingBreakdown>
}

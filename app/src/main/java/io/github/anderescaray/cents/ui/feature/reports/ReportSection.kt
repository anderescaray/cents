package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.runtime.Composable
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.ui.feature.reports.section.BalanceOverTimeSectionSource
import io.github.anderescaray.cents.ui.feature.reports.section.CategoryTrendSectionSource
import io.github.anderescaray.cents.ui.feature.reports.section.MonthlyFlowSectionSource
import io.github.anderescaray.cents.ui.feature.reports.section.PeriodComparisonSectionSource
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingSectionSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A view on the Reports tab is an **additive unit**, and this file is the seam that makes it
 * one.
 *
 * The tab started as two charts written into one view model, one screen, one state class,
 * one repository interface and one repository class. A third view meant editing all five in
 * the middle, and three agents adding three views at once meant five conflicts each. So a
 * view now owns everything about itself and the tab owns only the order they appear in.
 *
 * **What a new view has to touch**, and this list is meant to be checked rather than
 * believed:
 *
 * | File | What it adds |
 * |---|---|
 * | `domain/model/<View>.kt` | new — its model |
 * | `domain/repository/reports/<View>Repository.kt` | new — one method |
 * | `data/repository/reports/Room<View>Repository.kt` | new — the mapping |
 * | `ui/feature/reports/section/<View>Section.kt` | new — its state, its source, its rows |
 * | `di/RepositoryModule.kt` | one `@Binds` line |
 * | `ReportSections` below | one constructor parameter and one list entry |
 * | `data/local/dao/TransactionDao.kt` | one `@Query` and its row class, appended |
 * | `values/strings.xml`, `values-es/strings.xml` | its own strings, appended |
 *
 * Nothing in [ReportsViewModel] or [ReportsScreen] changes at all: the view model combines
 * whatever flows [ReportSections] hands it, and the screen renders whatever states come
 * back, in order, without knowing what any of them are.
 *
 * The DAO is the one shared file with more than a line in it, and it is deliberate: reports
 * queries live beside the transaction queries they are made of, and moving them into a
 * `ReportsDao` would have been a deletion from a file another agent is editing right now.
 * It is the obvious next cut if the appends there start colliding.
 *
 * Deliberately not `sealed`: a sealed interface has to have every implementation in its own
 * package, which would put each new view's file back beside the tab's plumbing instead of in
 * `section/` with its own kind. Nothing here needs an exhaustive `when` — a section draws
 * itself — so the constraint would buy nothing and cost the layout that makes the seam
 * visible.
 */
interface ReportSectionState {
    /**
     * The `LazyColumn` item key. Stable across periods, because a period change replaces the
     * data inside a section rather than replacing the section.
     */
    val key: String

    /**
     * The section, drawn.
     *
     * Rendering lives on the state rather than on the source so that [ReportsScreen] keeps
     * its stateless overload: a screen that took the sources would need Hilt to be testable,
     * and every Compose test on this tab depends on it not needing that.
     */
    @Composable
    fun Content(actions: ReportActions)
}

/**
 * What a section can ask the tab to do.
 *
 * One entry so far, and every view that drills down means the same thing by it: "the
 * movements this is made of". Handing over a [TransactionFilter] rather than the thing that
 * was tapped is what keeps the tab from knowing what a slice, a weekday or a category is.
 */
data class ReportActions(
    val onDrillDown: (TransactionFilter) -> Unit,
)

/**
 * Turns a [ReportScope] into one section's data. Injected once and stateless.
 *
 * One parameter rather than the period and currency it started as, because narrowing the tab
 * to an account is a question every view has to be asked at once — a ring showing one account
 * beside a balance line showing all of them is a screen that contradicts itself. A view that
 * ignores [ReportScope.accountId] is unfinished, not opted out.
 *
 * Note what it does *not* take: no click handlers, no navigation, no other section's data.
 * A source that needed any of those would be a section that is not additive.
 */
interface ReportSectionSource {
    fun observe(scope: ReportScope): Flow<ReportSectionState>
}

/**
 * The views on the Reports tab, in the order they appear on it.
 *
 * An explicit list rather than a Hilt multibinding: a `Set` has no order, and ordering by a
 * number on each section swaps a merge conflict anyone can see for two views quietly
 * claiming the same position. Two lines is a cheap price for the tab having one place that
 * says what it is made of.
 */
@Singleton
class ReportSections
    @Inject
    constructor(
        spending: SpendingSectionSource,
        monthlyFlow: MonthlyFlowSectionSource,
        balance: BalanceOverTimeSectionSource,
        comparison: PeriodComparisonSectionSource,
        trend: CategoryTrendSectionSource,
    ) {
        val inOrder: List<ReportSectionSource> =
            listOf(spending, monthlyFlow, balance, comparison, trend)
    }

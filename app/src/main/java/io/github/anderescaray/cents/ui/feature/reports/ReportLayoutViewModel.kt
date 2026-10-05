package io.github.anderescaray.cents.ui.feature.reports

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.ReportLayout
import io.github.anderescaray.cents.domain.repository.ReportLayoutRepository
import io.github.anderescaray.cents.ui.feature.reports.section.BalanceOverTimeSection
import io.github.anderescaray.cents.ui.feature.reports.section.CategoryTrendSection
import io.github.anderescaray.cents.ui.feature.reports.section.MonthlyFlowSection
import io.github.anderescaray.cents.ui.feature.reports.section.PeriodComparisonSection
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingByWeekdaySection
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingSection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Every card the Reports tab can show, in the order a fresh install shows them, with the name the
 * customise screen lists it under. Budgets are a card like the charts, so they can move too.
 */
enum class ReportCard(
    val key: String,
    @param:StringRes val labelRes: Int,
) {
    BUDGETS(BUDGETS_KEY, R.string.budgets_title),
    SPENDING(SpendingSection.KEY, R.string.reports_spending_header),
    FLOW(MonthlyFlowSection.KEY, R.string.reports_flow_header),
    BALANCE(BalanceOverTimeSection.KEY, R.string.reports_balance_header),
    WEEKDAY(SpendingByWeekdaySection.KEY, R.string.reports_weekday_header),
    COMPARISON(PeriodComparisonSection.KEY, R.string.reports_comparison_header),
    TREND(CategoryTrendSection.KEY, R.string.reports_trend_header),
    ;

    companion object {
        val keys: List<String> = entries.map { it.key }

        fun of(key: String): ReportCard? = entries.firstOrNull { it.key == key }
    }
}

/** The key of the budgets card, which is not a section of its own. */
const val BUDGETS_KEY = "budgets"

/** One row of the customise screen. */
@Immutable
data class ReportCardChoice(
    val card: ReportCard,
    val visible: Boolean,
)

@Immutable
data class ReportLayoutUiState(
    val layout: ReportLayout = ReportLayout(),
    /**
     * False until the stored arrangement has been read. Drawing the default one meanwhile is what
     * made hidden cards flash in and the order reshuffle a frame later.
     */
    val loaded: Boolean = false,
) {
    /** The cards in the owner's order, hidden ones included: what the customise screen lists. */
    val choices: List<ReportCardChoice>
        get() =
            layout.arrange(ReportCard.keys).mapNotNull { key ->
                ReportCard.of(key)?.let { ReportCardChoice(it, layout.isVisible(key)) }
            }

    /** The keys the tab draws, in order. */
    val shown: List<String> get() = layout.arrange(ReportCard.keys).filter(layout::isVisible)
}

/**
 * The arrangement of the Reports tab: read by the tab to know what to draw in which order, and
 * changed by the customise screen. One view model for both, so they cannot disagree.
 */
@HiltViewModel
class ReportLayoutViewModel
    @Inject
    constructor(
        private val repository: ReportLayoutRepository,
    ) : ViewModel() {
        val uiState: StateFlow<ReportLayoutUiState> =
            repository
                .observe()
                .map { ReportLayoutUiState(layout = it, loaded = true) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReportLayoutUiState())

        fun move(
            card: ReportCard,
            by: Int,
        ) = change { it.move(card.key, by, ReportCard.keys) }

        fun setVisible(
            card: ReportCard,
            visible: Boolean,
        ) = change { it.withVisible(card.key, visible) }

        fun reset() = change { ReportLayout() }

        /**
         * Changes the stored arrangement rather than the one on screen, one change at a time. Two
         * quick taps would otherwise each start from the arrangement before the other, and the
         * second save would undo the first; a tap before the first read would start from the
         * default and wipe the saved order.
         */
        private fun change(edit: (ReportLayout) -> ReportLayout) {
            viewModelScope.launch {
                saving.withLock { repository.save(edit(repository.observe().first())) }
            }
        }

        private val saving = Mutex()

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

package io.github.anderescaray.cents.ui.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * The periods offered on the selector.
 *
 * A preset is a *rule* for a range rather than a range: "this month" has to mean whatever
 * month it is when the screen is opened, not the month it was when the enum was written.
 * [CUSTOM] is the escape hatch, and it is the only one carrying dates of its own.
 */
enum class PeriodPreset {
    WEEK,
    MONTH,
    QUARTER,
    YEAR,
    CUSTOM,
}

/**
 * What the Reports tab is showing.
 *
 * Note what is *not* here: a field per chart. [sections] is whatever [ReportSections] was
 * built with, in that order, each one already reduced to the thing it draws — so adding a
 * fourth view changes this class not at all.
 */
data class ReportsUiState(
    val preset: PeriodPreset = PeriodPreset.MONTH,
    val period: ReportPeriod = ReportPeriod.Month(YearMonth.now()),
    val sections: List<ReportSectionState> = emptyList(),
    val isLoading: Boolean = true,
    /**
     * True when the app has no accounts at all, which is a different screen from a period
     * with nothing in it: one says "start here", the other says "nothing happened".
     */
    val hasNoAccounts: Boolean = false,
) {
    /** The custom range's endpoints, so the two date rows have something to show. */
    val customFrom: LocalDate get() = period.from

    val customTo: LocalDate get() = period.to

    /** The one section of a given kind, for the screens and the tests that want just one. */
    inline fun <reified T : ReportSectionState> section(): T? = sections.filterIsInstance<T>().firstOrNull()
}

/**
 * The Reports tab, reading the database instead of inventing content.
 *
 * Nothing here adds up transactions, and nothing here knows what any chart is. Its two jobs
 * are deciding *which period* is being asked about and re-asking every section when that
 * changes; the sections come from [ReportSections] and answer for themselves.
 */
@HiltViewModel
class ReportsViewModel
    @Inject
    constructor(
        private val sections: ReportSections,
        accountRepository: AccountRepository,
        private val filters: TransactionFilterStore,
        private val clock: Clock,
    ) : ViewModel() {
        private val selection = MutableStateFlow(defaultSelection())

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<ReportsUiState> =
            combine(
                selection,
                accountRepository.observeAccounts(includeArchived = true),
            ) { selected, accounts -> selected to accounts.firstOrNull()?.currency }
                .flatMapLatest { (selected, currency) ->
                    if (currency == null) {
                        flowOf(
                            ReportsUiState(
                                preset = selected.preset,
                                period = selected.period,
                                isLoading = false,
                                hasNoAccounts = true,
                            ),
                        )
                    } else {
                        // Every section answers a question about the same period, so they
                        // are combined rather than collected apart: shown separately they can
                        // disagree for a frame, and a donut for August beside bars ending in
                        // July is a screen that contradicts itself.
                        combine(
                            sections.inOrder.map { it.observe(selected.period, currency) },
                        ) { states ->
                            ReportsUiState(
                                preset = selected.preset,
                                period = selected.period,
                                sections = states.toList(),
                                isLoading = false,
                            )
                        }
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = ReportsUiState(),
                )

        fun selectPreset(preset: PeriodPreset) {
            selection.update { current ->
                if (preset == PeriodPreset.CUSTOM) {
                    // Switching to Custom keeps the range that was on screen, so the two
                    // date rows open on the period just being looked at rather than on
                    // today twice. Filters learned this the hard way (device check d5).
                    Selection(PeriodPreset.CUSTOM, ReportPeriod.Custom(current.period.from, current.period.to))
                } else {
                    Selection(preset, periodFor(preset, LocalDate.now(clock)))
                }
            }
        }

        fun setCustomRange(
            from: LocalDate,
            to: LocalDate,
        ) {
            // A range entered backwards is straightened rather than refused: it produces an
            // empty chart that looks identical to a quiet month, with nothing saying why.
            val start = minOf(from, to)
            val end = maxOf(from, to)
            selection.value = Selection(PeriodPreset.CUSTOM, ReportPeriod.Custom(start, end))
        }

        /**
         * Narrows the transaction list to the movements a section handed over.
         *
         * The list is not handed a copy of anything: it is handed the same filter it would
         * have got from the filter form, so the drill-down is the feature that already exists
         * rather than a second filtered screen. Which movements those are is the section's
         * business — this method never learns what was tapped. The caller navigates
         * afterwards.
         *
         * The search text is cleared along with it. A drill-down promises "the movements
         * behind this", and a word still sitting in the search field from ten minutes ago
         * would quietly narrow that promise further with nothing on the chart saying so.
         */
        fun drillDown(filter: TransactionFilter) {
            filters.apply(filter, clearQuery = true)
        }

        private fun defaultSelection(): Selection {
            val today = LocalDate.now(clock)
            return Selection(PeriodPreset.MONTH, periodFor(PeriodPreset.MONTH, today))
        }

        private data class Selection(
            val preset: PeriodPreset,
            val period: ReportPeriod,
        )

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        }
    }

/**
 * The range a preset means on a given day.
 *
 * The week runs Monday to Sunday and the quarter is the calendar quarter the day falls in —
 * both containing [today] rather than ending on it, so "this week" on a Tuesday is the whole
 * week and not the last seven days.
 */
internal fun periodFor(
    preset: PeriodPreset,
    today: LocalDate,
): ReportPeriod =
    when (preset) {
        PeriodPreset.WEEK -> {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            ReportPeriod.Week(monday, monday.plusDays(DAYS_IN_WEEK - 1))
        }

        PeriodPreset.MONTH -> {
            ReportPeriod.Month(YearMonth.from(today))
        }

        PeriodPreset.QUARTER -> {
            val firstMonth = ((today.monthValue - 1) / MONTHS_IN_QUARTER) * MONTHS_IN_QUARTER + 1
            val start = LocalDate.of(today.year, firstMonth, 1)
            val end = start.plusMonths((MONTHS_IN_QUARTER - 1).toLong())
            ReportPeriod.Quarter(start, end.withDayOfMonth(end.lengthOfMonth()))
        }

        PeriodPreset.YEAR -> {
            ReportPeriod.Year(today.year)
        }

        // Custom has no rule to derive: it is whatever two dates were picked, and the
        // caller carries them.
        PeriodPreset.CUSTOM -> {
            ReportPeriod.Custom(today.withDayOfMonth(1), today)
        }
    }

private const val DAYS_IN_WEEK = 7L
private const val MONTHS_IN_QUARTER = 3

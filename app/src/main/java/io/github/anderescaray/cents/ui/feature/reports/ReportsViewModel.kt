package io.github.anderescaray.cents.ui.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /**
     * Every account, archived ones included — the same list the balance line already counts.
     * Archiving means "stop offering it for new movements", not "the money never existed", so
     * an archived account that had a busy March is exactly what someone opens a report to look
     * at.
     */
    val accounts: List<Account> = emptyList(),
    /** Which account the whole tab is narrowed to, or null for all of them. */
    val accountId: Long? = null,
) {
    /**
     * Whether choosing is worth the room it takes. With one account there is nothing to
     * choose, and a picker offering a single answer is a row that only ever says what the
     * screen would have said anyway.
     */
    val canPickAccount: Boolean get() = accounts.size > 1

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
        private val today: Today,
    ) : ViewModel() {
        private val selection = MutableStateFlow(defaultSelection())

        init {
            // A preset means a range *around today*, so a new day can mean a new range: "this
            // month" at midnight on the first is the new month. A custom range is the owner's
            // own and stays exactly where they put it.
            viewModelScope.launch {
                today.date.drop(1).collect { day ->
                    selection.update { current ->
                        if (current.preset == PeriodPreset.CUSTOM) {
                            current
                        } else {
                            current.copy(period = periodFor(current.preset, day))
                        }
                    }
                }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<ReportsUiState> =
            combine(
                selection,
                accountRepository.observeAccounts(includeArchived = true),
                // In the key even when the range did not move: the comparison and the trend
                // read the day when they are asked, to know how much of the period has passed.
                today.date,
            ) { selected, accounts, _ -> selected to accounts }
                .flatMapLatest { (selected, accounts) ->
                    // An account the tab was narrowed to can stop existing while the tab is
                    // open — deleted from the Accounts tab, movements and all (D7). Falling
                    // back to every account is the only honest answer; the alternative is a
                    // tab reporting on nothing, headed by the name of something gone.
                    val accountId = selected.accountId?.takeIf { id -> accounts.any { it.id == id } }
                    val currency =
                        accounts.firstOrNull { it.id == accountId }?.currency
                            ?: accounts.firstOrNull()?.currency

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
                        val scope = ReportScope(selected.period, currency, accountId)
                        // Every section answers a question about the same scope, so they are
                        // combined rather than collected apart: shown separately they can
                        // disagree for a frame, and a donut for August beside bars ending in
                        // July is a screen that contradicts itself. That is the same argument
                        // for the account being part of the scope rather than something one
                        // view applies for itself.
                        combine(
                            sections.inOrder.map { it.observe(scope) },
                        ) { states ->
                            ReportsUiState(
                                preset = selected.preset,
                                period = selected.period,
                                sections = states.toList(),
                                isLoading = false,
                                accounts = accounts,
                                accountId = accountId,
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
                    current.copy(
                        preset = PeriodPreset.CUSTOM,
                        period = ReportPeriod.Custom(current.period.from, current.period.to),
                    )
                } else {
                    // Copied rather than rebuilt: changing the period is not a reason to stop
                    // looking at the account that was being looked at.
                    current.copy(preset = preset, period = periodFor(preset, today.date.value))
                }
            }
        }

        /**
         * Narrows every view to one account, or to all of them with null.
         *
         * One selection for the whole tab rather than one per chart, for the same reason the
         * period is one: the charts are three answers about the same money, and letting them
         * disagree about whose money it is makes the tab unreadable.
         */
        fun selectAccount(accountId: Long?) {
            selection.update { it.copy(accountId = accountId) }
        }

        fun setCustomRange(
            from: LocalDate,
            to: LocalDate,
        ) {
            // A range entered backwards is straightened rather than refused: it produces an
            // empty chart that looks identical to a quiet month, with nothing saying why.
            val start = minOf(from, to)
            val end = maxOf(from, to)
            selection.update {
                it.copy(preset = PeriodPreset.CUSTOM, period = ReportPeriod.Custom(start, end))
            }
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

        private fun defaultSelection(): Selection =
            Selection(PeriodPreset.MONTH, periodFor(PeriodPreset.MONTH, today.date.value))

        private data class Selection(
            val preset: PeriodPreset,
            val period: ReportPeriod,
            val accountId: Long? = null,
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

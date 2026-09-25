package io.github.anderescaray.cents.ui.feature.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.Group
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.GroupRepository
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import io.github.anderescaray.cents.ui.util.AmountEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/** Which end of the date range a picker is currently open for. */
enum class DateBound { FROM, TO }

/**
 * A named span of days, so the common questions are one tap rather than two calendars.
 *
 * [CUSTOM] is not a preset but the absence of one: it is what the form falls back to when
 * the dates in hand match none of the others, which is also what selecting it means.
 */
enum class DateRangePreset { ANY, THIS_MONTH, LAST_MONTH, THIS_YEAR, CUSTOM }

data class TransactionFiltersUiState(
    /** Edited in place and only handed back on Apply, so Cancel really cancels. */
    val draft: TransactionFilter = TransactionFilter.None,
    /**
     * Which date row is ticked. **Held, not derived from the dates.** Deriving it looks
     * tidier and is a trap: "between two dates" would be unreachable the moment a preset had
     * been chosen, because the range it produced would keep reading back as that preset and
     * the two calendars would never appear. A pristine form opened on the last day of a
     * month walked into the same wall.
     */
    val preset: DateRangePreset = DateRangePreset.ANY,
    /**
     * The two amount bounds **as they are being typed**, in euros, held next to the numbers
     * they parse to rather than derived from them.
     *
     * Deriving them back from [TransactionFilter.minAmountMinor] would erase the separator
     * the instant it was typed — `2,` and `2` are the same 200 cents — so the comma key would
     * appear broken on the one screen where the owner is watching for it.
     */
    val minAmountInput: String = AmountEntry.EMPTY,
    val maxAmountInput: String = AmountEntry.EMPTY,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Every group, by name; the section is left off the form while there are none. */
    val groups: List<Group> = emptyList(),
    /** The one currency the app works in, for saying the amount range back in money. */
    val currency: String = "EUR",
    val today: LocalDate = LocalDate.now(),
    val openPicker: DateBound? = null,
) {
    /** Nothing is narrowing the list, so there is nothing for Clear to do. */
    val isPristine: Boolean get() = draft.activeCount == 0
}

/**
 * The filter form: everything that narrows the list except the search field, which lives on
 * the list itself.
 *
 * It edits a **draft** rather than the live filter. Applying a half-built filter row by row
 * would make Cancel meaningless and would churn the query underneath a screen the owner
 * cannot even see — so the store is written once, on Apply.
 */
@HiltViewModel
class TransactionFiltersViewModel
    @Inject
    constructor(
        private val filters: TransactionFilterStore,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        groupRepository: GroupRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val form = MutableStateFlow(TransactionFiltersUiState(today = LocalDate.now(clock)))

        val uiState: StateFlow<TransactionFiltersUiState> = form.asStateFlow()

        /**
         * Set by "show everything again". Sticky until the form is left, because that row
         * promises a reset and picking something afterwards is a new narrowing, not an undo.
         */
        private var clearsQuery = false

        init {
            val current = filters.filter.value
            form.update {
                it.copy(
                    draft = current,
                    preset = current.presetFor(LocalDate.now(clock)),
                    minAmountInput = current.minAmountMinor.asInput(),
                    maxAmountInput = current.maxAmountMinor.asInput(),
                )
            }
            viewModelScope.launch {
                // Archived ones are included: a filter is a question about history, and
                // history recorded against an archived account or category is still there.
                combine(
                    accountRepository.observeAccounts(includeArchived = true),
                    categoryRepository.observeAllCategories(),
                ) { accounts, categories -> accounts to categories }
                    .collect { (accounts, categories) ->
                        form.update {
                            it.copy(
                                accounts = accounts,
                                categories = categories,
                                // From the accounts rather than the locale: an account is
                                // stamped with the currency in force when it was created, and
                                // an install predating D2 may hold one that is not the
                                // device's. Saying the range back in the wrong currency would
                                // be a confidently wrong number.
                                currency = accounts.firstOrNull()?.currency ?: it.currency,
                            )
                        }
                    }
            }
            viewModelScope.launch {
                groupRepository.observeGroups().collect { groups -> form.update { it.copy(groups = groups) } }
            }
        }

        fun setKind(kind: TransactionKind?) = edit { it.copy(kind = kind) }

        fun setAccount(accountId: Long?) = edit { it.copy(accountId = accountId) }

        fun setCategory(category: CategoryFilter) = edit { it.copy(category = category) }

        /** One group's movements, or null for every movement whatever group it is in. */
        fun setGroup(groupId: Long?) = edit { it.copy(groupId = groupId) }

        /** One day of the week, or null for every day — what a weekday bar drills down to. */
        fun setWeekday(weekday: DayOfWeek?) = edit { it.copy(weekday = weekday) }

        /** A preset writes both ends at once; [DateRangePreset.CUSTOM] opens the calendars. */
        fun setPreset(preset: DateRangePreset) {
            val today = LocalDate.now(clock)
            form.update { it.copy(preset = preset) }
            edit { draft ->
                when (preset) {
                    DateRangePreset.ANY -> {
                        draft.copy(from = null, to = null)
                    }

                    DateRangePreset.THIS_MONTH -> {
                        draft.copy(from = today.withDayOfMonth(1), to = today.endOfMonth())
                    }

                    DateRangePreset.LAST_MONTH -> {
                        today.minusMonths(1).let { previous ->
                            draft.copy(from = previous.withDayOfMonth(1), to = previous.endOfMonth())
                        }
                    }

                    DateRangePreset.THIS_YEAR -> {
                        draft.copy(
                            from = today.withDayOfYear(1),
                            to = today.withDayOfYear(today.lengthOfYear()),
                        )
                    }

                    // Give the two calendars somewhere to start rather than nowhere.
                    DateRangePreset.CUSTOM -> {
                        draft.copy(
                            from = draft.from ?: today.withDayOfMonth(1),
                            to = draft.to ?: today,
                        )
                    }
                }
            }
        }

        fun openPicker(bound: DateBound) = form.update { it.copy(openPicker = bound) }

        fun dismissPicker() = form.update { it.copy(openPicker = null) }

        fun setBound(date: LocalDate) {
            val bound = form.value.openPicker ?: return
            form.update { state ->
                val draft =
                    when (bound) {
                        DateBound.FROM -> state.draft.copy(from = date)
                        DateBound.TO -> state.draft.copy(to = date)
                    }
                // A hand-picked date is a custom range even if it lands on a preset's edges.
                state.copy(draft = draft, preset = DateRangePreset.CUSTOM, openPicker = null)
            }
        }

        /**
         * Typed in euros with a comma, exactly as on the movement keypad — the same
         * [AmountEntry] rules, so 25 is 25 € here and 25 € there.
         */
        fun setMinAmount(typed: String) {
            val input = AmountEntry.sanitize(typed)
            form.update {
                it.copy(minAmountInput = input, draft = it.draft.copy(minAmountMinor = input.asBound()))
            }
        }

        fun setMaxAmount(typed: String) {
            val input = AmountEntry.sanitize(typed)
            form.update {
                it.copy(maxAmountInput = input, draft = it.draft.copy(maxAmountMinor = input.asBound()))
            }
        }

        /**
         * Empties the form without leaving it: Apply is still what commits the change.
         *
         * The search text goes with it, because the row says "show everything again" and a
         * list still narrowed to what was typed is not everything.
         */
        fun clear() {
            clearsQuery = true
            form.update {
                it.copy(
                    draft = TransactionFilter.None,
                    preset = DateRangePreset.ANY,
                    minAmountInput = AmountEntry.EMPTY,
                    maxAmountInput = AmountEntry.EMPTY,
                )
            }
        }

        fun apply() = filters.apply(form.value.draft, clearQuery = clearsQuery)

        private fun edit(change: (TransactionFilter) -> TransactionFilter) =
            form.update { it.copy(draft = change(it.draft)) }
    }

/**
 * Blank means "no bound", which is not the same number as zero.
 *
 * The digits themselves are already capped by [AmountEntry.sanitize], so a pasted or
 * held-down run of them cannot overflow `Long` and come back as null — which this function
 * spends its meaning on, and which would silently drop the bound while the owner was still
 * typing into the field.
 */
private fun String.asBound(): Long? = takeIf { it.isNotEmpty() }?.let(AmountEntry::toMinorUnits)

/**
 * What the field shows when the form opens on a filter the list already carries.
 *
 * Zero is spelled out rather than left to [AmountEntry.fromMinorUnits], which answers "" for
 * it: an amount of nothing was never typed into a movement, but "at least 0 €" is a bound
 * somebody did type here, and it must survive the form being reopened.
 */
private fun Long?.asInput(): String =
    when (this) {
        null -> AmountEntry.EMPTY
        0L -> "0"
        else -> AmountEntry.fromMinorUnits(this)
    }

private fun LocalDate.endOfMonth(): LocalDate = withDayOfMonth(lengthOfMonth())

/**
 * Which preset these dates describe. Used **once**, when the form opens on a filter it did
 * not build, so reopening it shows "This month" ticked rather than a custom range that
 * happens to be this month. After that the choice is remembered rather than re-guessed.
 */
private fun TransactionFilter.presetFor(today: LocalDate): DateRangePreset {
    if (from == null && to == null) return DateRangePreset.ANY
    val previous = today.minusMonths(1)
    return when {
        from == today.withDayOfMonth(1) && to == today.endOfMonth() -> {
            DateRangePreset.THIS_MONTH
        }

        from == previous.withDayOfMonth(1) && to == previous.endOfMonth() -> {
            DateRangePreset.LAST_MONTH
        }

        from == today.withDayOfYear(1) && to == today.withDayOfYear(today.lengthOfYear()) -> {
            DateRangePreset.THIS_YEAR
        }

        else -> {
            DateRangePreset.CUSTOM
        }
    }
}

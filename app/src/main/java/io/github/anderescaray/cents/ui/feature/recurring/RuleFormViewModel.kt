package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.Reminder
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.holdsInvestments
import io.github.anderescaray.cents.domain.model.takesIncomeAndExpenses
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.AmountEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Which date the calendar is being opened for. */
enum class RuleDateField {
    START,
    END,
}

@Immutable
data class RuleFormUiState(
    val name: String = "",
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountInput: String = AmountEntry.EMPTY,
    val accounts: List<Account> = emptyList(),
    /** Brokers exist and are not offered (D32), so the account section says where they went. */
    val brokersLeftOut: Boolean = false,
    val categories: List<Category> = emptyList(),
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val intervalInput: String = "",
    val startDate: LocalDate = LocalDate.EPOCH,
    val endDate: LocalDate? = null,
    val today: LocalDate = LocalDate.EPOCH,
    val mode: RuleMode = RuleMode.AUTOMATIC,
    val currency: String = "EUR",
    val isEditing: Boolean = false,
    /** Whether the rule is still generating. A stopped one offers deleting instead of stopping. */
    val ruleActive: Boolean = true,
    val pickingDate: RuleDateField? = null,
    val pickingFrequency: Boolean = false,
    /** The nudges this rule carries, soonest first. Held here until the rule is saved. */
    val reminders: List<Reminder> = emptyList(),
    val addingReminder: Boolean = false,
    val reminderDaysInput: String = "",
    val reminderBefore: Boolean = true,
    /** Set once it is known that stopping is the honest form of deleting (PRD §4.4). */
    val generatedCount: Int = 0,
    val confirmingRemoval: Boolean = false,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    val amount: Money get() = Money(AmountEntry.toMinorUnits(amountInput), currency)

    val intervalDays: Int? get() = intervalInput.toIntOrNull()?.takeIf { it >= 1 }

    val needsInterval: Boolean get() = frequency == Frequency.EVERY_N_DAYS

    /** Whether the amount is one the owner will have to supply each time. */
    val asksForAmount: Boolean get() = mode == RuleMode.CONFIRM

    val reminderDays: Int? get() = reminderDaysInput.toIntOrNull()?.takeIf { it in 0..MAX_REMINDER_DAYS }

    /** What the reminder being typed would come to, signed the way it is stored. */
    val reminderOffset: Int?
        get() = reminderDays?.let { days -> if (days == 0 || !reminderBefore) days else -days }

    /** The same reminder twice is the same notification twice. */
    val reminderIsDuplicate: Boolean
        get() = reminderOffset != null && reminders.any { it.offsetDays == reminderOffset }

    val canAddReminder: Boolean get() = reminderOffset != null && !reminderIsDuplicate

    /** Whether the rule has ever produced a movement. */
    val hasHistory: Boolean get() = generatedCount > 0

    /**
     * Whether the one removal button stops the rule rather than deleting it.
     *
     * **Stopping first, deleting only afterwards**, and the order is the point. A running
     * subscription with charges behind it is almost never meant to be erased — "cancel Netflix"
     * means stop billing me, and the rule is what explains eleven movements in the list. So that
     * is what the button does. Once it is stopped it can be deleted outright, which is the way
     * out of an account that cannot be deleted while a rule records into it; the movements stay
     * either way, because `transactions.ruleId` is `SET NULL` (rule 8).
     */
    val stopsRatherThanDeletes: Boolean get() = ruleActive && hasHistory

    val canSave: Boolean
        get() =
            name.isNotBlank() &&
                amount.minorUnits > 0 &&
                accountId != null &&
                (!needsInterval || intervalDays != null) &&
                (endDate == null || endDate >= startDate) &&
                !saving
}

/** Nobody is reminded about something a thousand days away, and three digits is the field. */
private const val MAX_REMINDER_DIGITS = 3
private const val MAX_REMINDER_DAYS = 365

/**
 * Defining something that repeats.
 *
 * The one field worth a second look is the **start date**, which is the anchor rather than just
 * the beginning: every occurrence is counted from it, so a rule starting on the 31st is a rule
 * that lands on the 31st whenever the month has one. It also decides the backlog — a rule
 * entered in April with a start date in January will generate the three charges it missed, which
 * is what somebody entering their rent for the first time expects.
 */
@HiltViewModel
class RuleFormViewModel
    @Inject
    constructor(
        private val repository: RecurringRuleRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        savedStateHandle: SavedStateHandle,
        clock: Clock,
    ) : ViewModel() {
        private val ruleId: Long? =
            savedStateHandle
                .toRoute<Destination.RuleForm>()
                .ruleId
                .takeIf { it != Destination.RuleForm.NEW }

        private val today = LocalDate.now(clock)
        private val form = MutableStateFlow(RuleFormUiState(startDate = today, today = today))
        val uiState: StateFlow<RuleFormUiState> = form.asStateFlow()

        private var allCategories: List<Category> = emptyList()
        private var allAccounts: List<Account> = emptyList()

        /** The account the rule being edited is in, offered even when a new rule could not be. */
        private var ownAccountId: Long? = null

        init {
            viewModelScope.launch { load() }
            viewModelScope.launch {
                combine(
                    accountRepository.observeAccounts(),
                    categoryRepository.observeAllCategories(),
                ) { accounts, categories -> accounts to categories }
                    .collect { (accounts, categories) ->
                        allCategories = categories
                        allAccounts = accounts
                        form.update { it.withOptions() }
                    }
            }
        }

        private suspend fun load() {
            val id = ruleId ?: return
            val rule = repository.findById(id) ?: return
            val generated = repository.countGenerated(id)
            ownAccountId = rule.accountId
            form.update { state ->
                state
                    .copy(
                        isEditing = true,
                        name = rule.name,
                        kind = rule.kind,
                        amountInput = AmountEntry.fromMinorUnits(rule.amount.minorUnits),
                        accountId = rule.accountId,
                        categoryId = rule.categoryId,
                        frequency = rule.frequency,
                        intervalInput = rule.intervalDays?.toString().orEmpty(),
                        startDate = rule.startDate,
                        endDate = rule.endDate,
                        mode = rule.mode,
                        currency = rule.amount.currency,
                        ruleActive = rule.active,
                        reminders = repository.remindersFor(id),
                        generatedCount = generated,
                    ).withOptions()
            }
        }

        /**
         * What the pickers offer. Never a broker for a new rule (D32): a subscription writes
         * ordinary incomes and expenses unattended. A rule already in one keeps it, shown.
         */
        private fun RuleFormUiState.withOptions(): RuleFormUiState {
            val offered =
                allCategories.filter { category ->
                    category.kind == kind.toCategoryKind() &&
                        (!category.archived || category.id == categoryId)
                }
            val accounts = allAccounts.filter { it.takesIncomeAndExpenses || it.id == ownAccountId }
            val defaulted = if (accountId == null) accounts.firstOrNull() else null
            return copy(
                accounts = accounts,
                brokersLeftOut = allAccounts.any { it.holdsInvestments && it.id != ownAccountId },
                categories = offered,
                accountId = accountId ?: defaulted?.id,
                currency = if (isEditing) currency else defaulted?.currency ?: currency,
                categoryId = categoryId?.takeIf { id -> offered.any { it.id == id } },
            )
        }

        fun setName(name: String) = form.update { it.copy(name = name, writeFailed = false) }

        fun appendDigit(digit: Char) {
            if (!digit.isDigit()) return
            form.update { it.copy(amountInput = AmountEntry.append(it.amountInput, digit)) }
        }

        fun appendDecimalSeparator() =
            form.update { it.copy(amountInput = AmountEntry.append(it.amountInput, AmountEntry.SEPARATOR)) }

        fun deleteDigit() = form.update { it.copy(amountInput = AmountEntry.delete(it.amountInput)) }

        fun setKind(kind: TransactionKind) = form.update { it.copy(kind = kind, categoryId = null).withOptions() }

        fun selectAccount(id: Long) =
            form.update { state ->
                val account = state.accounts.firstOrNull { it.id == id }
                state.copy(accountId = id, currency = account?.currency ?: state.currency)
            }

        fun selectCategory(id: Long?) = form.update { it.copy(categoryId = id) }

        fun openFrequencyPicker() = form.update { it.copy(pickingFrequency = true) }

        fun dismissFrequencyPicker() = form.update { it.copy(pickingFrequency = false) }

        fun setFrequency(frequency: Frequency) =
            form.update { it.copy(frequency = frequency, pickingFrequency = false) }

        /** Digits only, and no more than three: a rule that comes round every 999 days is the
         *  furthest apart this is meant to express.
         */
        fun setInterval(typed: String) =
            form.update {
                it.copy(
                    intervalInput =
                        typed
                            .filter { character ->
                                character.isDigit()
                            }.take(MAX_INTERVAL_DIGITS),
                )
            }

        fun setMode(mode: RuleMode) =
            form.update {
                // A fixed subscription is announced, never chased afterwards: the movement is
                // already in the list, so "Netflix was charged yesterday" is the app telling the
                // owner something they can read one screen away. Switching to a fixed amount
                // therefore turns any reminder that was set for *after* the date into the same
                // number of days *before* it, which is the thing that was actually wanted.
                val kept = if (mode == RuleMode.CONFIRM) it.reminders else it.reminders.map(Reminder::asBefore)
                it.copy(mode = mode, reminders = kept.distinctBy(Reminder::offsetDays))
            }

        /** Opens on *the day before*, the reminder most often wanted, so one tap can add it. */
        fun startAddingReminder() =
            form.update { it.copy(addingReminder = true, reminderDaysInput = "1", reminderBefore = true) }

        fun cancelAddingReminder() = form.update { it.copy(addingReminder = false) }

        fun setReminderDays(typed: String) =
            form.update {
                it.copy(reminderDaysInput = typed.filter(Char::isDigit).take(MAX_REMINDER_DIGITS))
            }

        fun setReminderBefore(before: Boolean) = form.update { it.copy(reminderBefore = before) }

        /** The stepper: one day more or fewer, never below *on the day* or past a year. */
        fun stepReminderDays(delta: Int) =
            form.update {
                val days = ((it.reminderDays ?: 0) + delta).coerceIn(0, MAX_REMINDER_DAYS)
                it.copy(reminderDaysInput = days.toString())
            }

        /** A suggestion chip: the days and the side at once, signed the way a reminder is stored. */
        fun chooseReminder(offsetDays: Int) =
            form.update {
                it.copy(
                    reminderDaysInput = kotlin.math.abs(offsetDays).toString(),
                    reminderBefore = offsetDays <= 0,
                )
            }

        fun confirmReminder() =
            form.update { state ->
                val offset = state.reminderOffset
                if (!state.canAddReminder || offset == null) {
                    state
                } else {
                    state.copy(
                        reminders = (state.reminders + Reminder(offsetDays = offset)).sortedBy { it.offsetDays },
                        addingReminder = false,
                        reminderDaysInput = "",
                    )
                }
            }

        fun removeReminder(offsetDays: Int) =
            form.update { it.copy(reminders = it.reminders.filterNot { r -> r.offsetDays == offsetDays }) }

        fun openDatePicker(field: RuleDateField) = form.update { it.copy(pickingDate = field) }

        fun dismissDatePicker() = form.update { it.copy(pickingDate = null) }

        fun setDate(date: LocalDate) =
            form.update { state ->
                when (state.pickingDate) {
                    RuleDateField.START -> state.copy(startDate = date, pickingDate = null)
                    RuleDateField.END -> state.copy(endDate = date, pickingDate = null)
                    null -> state.copy(pickingDate = null)
                }
            }

        /** Takes the end date off again, for a rule that turned out to have no end. */
        fun clearEndDate() = form.update { it.copy(endDate = null) }

        fun save() {
            val state = form.value
            if (!state.canSave) return
            form.update { it.copy(saving = true) }
            viewModelScope.launch {
                val rule =
                    RecurringRule(
                        id = ruleId ?: 0L,
                        name = state.name.trim(),
                        accountId = requireNotNull(state.accountId),
                        categoryId = state.categoryId,
                        kind = state.kind,
                        amount = state.amount,
                        frequency = state.frequency,
                        intervalDays = state.intervalDays.takeIf { state.needsInterval },
                        startDate = state.startDate,
                        endDate = state.endDate,
                        mode = state.mode,
                        // Whatever it was. Editing the price of a stopped subscription is not
                        // a request to start charging for it again.
                        active = state.ruleActive,
                        // Left alone on an edit: the repository keeps whatever the rule has
                        // already produced, so editing a price does not replay the year.
                        lastGenerated = null,
                    )
                val written =
                    runCatching {
                        val id =
                            if (ruleId == null) {
                                repository.createRule(rule)
                            } else {
                                repository.updateRule(
                                    rule.copy(lastGenerated = repository.findById(ruleId)?.lastGenerated),
                                )
                                ruleId
                            }
                        repository.setReminders(id, state.reminders.map(Reminder::offsetDays))
                        // **And then catch it up, here, before the screen closes.** The start
                        // date is the anchor *and* the backlog: a subscription entered in
                        // September that began in May is five charges that already happened, and
                        // the owner is looking at the movement list a second later. Leaving it to
                        // the next `ON_START` meant nothing appeared until the app had been
                        // closed and reopened, which reads exactly like the feature not working.
                        repository.generateDue()
                    }
                form.update {
                    it.copy(saving = false, writeFailed = written.isFailure, finished = written.isSuccess)
                }
            }
        }

        fun requestRemoval() = form.update { it.copy(confirmingRemoval = true) }

        fun cancelRemoval() = form.update { it.copy(confirmingRemoval = false) }

        /**
         * Stops the rule, or removes it.
         *
         * One button because it is one intention — *I am done with this* — and which of the two
         * it means is decided by [RuleFormUiState.stopsRatherThanDeletes] rather than by the
         * owner. Nothing it generated is touched on either path.
         */
        fun confirmRemoval() {
            val id = ruleId ?: return
            val stopping = form.value.stopsRatherThanDeletes
            viewModelScope.launch {
                val done =
                    runCatching {
                        if (stopping) repository.setActive(id, active = false) else repository.deleteRule(id)
                    }
                form.update {
                    it.copy(
                        confirmingRemoval = false,
                        writeFailed = done.isFailure,
                        finished = done.isSuccess,
                    )
                }
            }
        }

        private companion object {
            /** Every 999 days is the furthest apart this is meant to express. */
            const val MAX_INTERVAL_DIGITS = 3
        }
    }

private fun TransactionKind.toCategoryKind(): CategoryKind =
    when (this) {
        TransactionKind.INCOME -> CategoryKind.INCOME
        TransactionKind.EXPENSE -> CategoryKind.EXPENSE
    }

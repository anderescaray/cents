package io.github.anderescaray.cents.ui.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * Where the owner is in deciding what "delete" means for this account.
 *
 * A category can be detached from a movement and the movement survives, unclassified. An
 * account cannot: `transactions.accountId` is `RESTRICT`, and a movement with no account is
 * not a movement. So deleting an account that holds history is not one operation with an
 * obvious meaning — it is a question, and this is the shape of asking it (`DECISIONS.md` D7).
 */
enum class RemovalStep {
    /** Nothing is being asked. */
    NONE,

    /** No movements live in it, so one yes/no is the whole question. */
    CONFIRM_EMPTY,

    /** It holds movements: the sheet offers the two things delete could honestly mean. */
    CHOOSE,

    /** Which account the movements move into before this one goes. */
    PICK_TARGET,

    /** The only path in the app that destroys recorded history, asked again on its own. */
    CONFIRM_DESTRUCTIVE,
}

data class AccountEditUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val currency: String = "EUR",
    val colorArgb: Int = AccountPalette.first(),
    val openingDigits: String = "",
    val openingNegative: Boolean = false,
    val archived: Boolean = false,
    val transactionCount: Int = 0,
    val nameTaken: Boolean = false,
    val removalStep: RemovalStep = RemovalStep.NONE,
    /** Accounts the movements could move into: active, not this one, same currency. */
    val reassignTargets: List<Account> = emptyList(),
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    /** Typed as minor units, like every amount in the app (rule 1). */
    val openingBalance: Money
        get() {
            val magnitude = openingDigits.toLongOrNull() ?: 0L
            return Money(if (openingNegative) -magnitude else magnitude, currency)
        }

    val canSave: Boolean get() = name.isNotBlank() && !nameTaken && !saving

    /** The currency is fixed once movements exist: they are already denominated in it. */
    val currencyLocked: Boolean get() = !isNew && transactionCount > 0

    /** True while the owner is being asked something about deleting this account. */
    val removing: Boolean get() = removalStep != RemovalStep.NONE

    /**
     * Reassignment needs somewhere to reassign to. When there is nowhere, the choice is
     * still shown — disabled, with the reason — rather than quietly dropped.
     */
    val canReassign: Boolean get() = reassignTargets.isNotEmpty()
}

/**
 * Creating and editing one account.
 *
 * Deleting is the part with teeth. An empty account just goes. One that holds movements
 * cannot simply disappear — its movements would go with it — so the owner is asked which
 * they mean: move the history into another account and delete this one, or delete both.
 * The second is the only operation in the app that destroys recorded history, so it is
 * reached through a sheet and then a second confirmation that says how many movements are
 * at stake (`DECISIONS.md` D7).
 *
 * Archiving is a separate action rather than a consolation prize hidden behind "delete":
 * it means keep everything and stop offering the account, which is a different intention.
 */
@HiltViewModel
class AccountEditViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val accountId: Long? =
            savedStateHandle
                .toRoute<Destination.AccountEdit>()
                .accountId
                .takeIf { it != Destination.AccountEdit.NEW }

        private val form = MutableStateFlow(AccountEditUiState(isNew = accountId == null))

        val uiState: StateFlow<AccountEditUiState> = form.asStateFlow()

        private var takenNames: Set<String> = emptySet()
        private var edited = false

        init {
            viewModelScope.launch { load() }
            viewModelScope.launch {
                accountRepository.observeAccounts(includeArchived = true).collect { accounts ->
                    takenNames =
                        accounts
                            .filter { it.id != accountId }
                            .map { it.name.lowercase() }
                            .toSet()
                    form.update { it.copy(nameTaken = isNameTaken(it.name)) }
                }
            }
        }

        private suspend fun load() {
            val id = accountId
            if (id == null) {
                val used = accountRepository.observeAccounts(includeArchived = true).first()
                form.update { state ->
                    if (edited) {
                        state
                    } else {
                        state.copy(
                            currency = defaultCurrency(),
                            colorArgb = nextAccountColor(used.map { it.colorArgb }),
                        )
                    }
                }
                return
            }

            val account = accountRepository.findById(id) ?: return
            val count = accountRepository.countTransactions(id)
            form.update { state ->
                state.copy(
                    isNew = false,
                    name = if (edited) state.name else account.name,
                    type = if (edited) state.type else account.type,
                    currency = if (edited) state.currency else account.currency,
                    colorArgb = if (edited) state.colorArgb else account.colorArgb,
                    openingDigits =
                        if (edited) {
                            state.openingDigits
                        } else {
                            account.openingBalance.minorUnits
                                .let { if (it == 0L) "" else kotlin.math.abs(it).toString() }
                        },
                    openingNegative =
                        if (edited) state.openingNegative else account.openingBalance.minorUnits < 0,
                    archived = account.archived,
                    transactionCount = count,
                )
            }
        }

        fun setName(name: String) {
            edited = true
            form.update { it.copy(name = name, nameTaken = isNameTaken(name), writeFailed = false) }
        }

        fun setType(type: AccountType) {
            edited = true
            form.update { it.copy(type = type) }
        }

        fun setCurrency(currency: String) {
            edited = true
            form.update { if (it.currencyLocked) it else it.copy(currency = currency) }
        }

        fun setColor(colorArgb: Int) {
            edited = true
            form.update { it.copy(colorArgb = colorArgb) }
        }

        fun appendDigit(digit: Char) {
            if (!digit.isDigit()) return
            edited = true
            form.update { state ->
                val next = (state.openingDigits + digit).trimStart('0')
                if (next.length > MAX_DIGITS) state else state.copy(openingDigits = next)
            }
        }

        fun deleteDigit() {
            edited = true
            form.update { it.copy(openingDigits = it.openingDigits.dropLast(1)) }
        }

        /** An account can start in the red: a card with a balance owed, for instance. */
        fun toggleSign() {
            edited = true
            form.update { it.copy(openingNegative = !it.openingNegative) }
        }

        fun save() {
            val state = form.value
            if (!state.canSave) return
            form.update { it.copy(saving = true) }

            viewModelScope.launch {
                val id = accountId
                val written =
                    runCatching {
                        if (id == null) {
                            accountRepository.createAccount(
                                Account(
                                    id = 0,
                                    name = state.name.trim(),
                                    type = state.type,
                                    currency = state.currency,
                                    openingBalance = state.openingBalance,
                                    colorArgb = state.colorArgb,
                                    archived = false,
                                    sortOrder = nextSortOrder(),
                                ),
                            )
                        } else {
                            val existing = accountRepository.findById(id) ?: return@runCatching
                            accountRepository.updateAccount(
                                existing.copy(
                                    name = state.name.trim(),
                                    type = state.type,
                                    currency = state.currency,
                                    openingBalance = state.openingBalance,
                                    colorArgb = state.colorArgb,
                                ),
                            )
                        }
                    }
                // The name has a unique index of its own: if two writes race, the second
                // one fails, and the form has to say so rather than looking inert.
                form.update {
                    it.copy(
                        saving = false,
                        writeFailed = written.isFailure,
                        finished = written.isSuccess,
                    )
                }
            }
        }

        /**
         * Opens the question, after re-reading how many movements are actually at stake and
         * which accounts could take them. Both are read now rather than trusted from load:
         * the difference between them is the difference between deleting an empty account
         * and deleting a year of history.
         */
        fun requestRemoval() {
            val id = accountId ?: return
            viewModelScope.launch {
                val count = accountRepository.countTransactions(id)
                val targets = reassignTargets(id)
                form.update { state ->
                    state.copy(
                        transactionCount = count,
                        reassignTargets = targets,
                        removalStep =
                            if (count == 0) RemovalStep.CONFIRM_EMPTY else RemovalStep.CHOOSE,
                    )
                }
            }
        }

        fun cancelRemoval() = form.update { it.copy(removalStep = RemovalStep.NONE) }

        /** Deletes an account nothing lives in. */
        fun confirmEmptyRemoval() {
            val id = accountId ?: return
            // A movement could have landed in the account between counting and confirming,
            // and the foreign key would refuse the delete: report that rather than letting
            // it escape the coroutine.
            finishRemoval { accountRepository.deleteAccount(id) }
        }

        /** Step two of the harmless path: which account the movements move into. */
        fun chooseReassign() {
            if (!form.value.canReassign) return
            form.update { it.copy(removalStep = RemovalStep.PICK_TARGET) }
        }

        /** Step two of the destructive path: ask again, alone, naming what is lost. */
        fun chooseDestructive() = form.update { it.copy(removalStep = RemovalStep.CONFIRM_DESTRUCTIVE) }

        /** Moves every movement into [targetId] and then deletes this account. */
        fun reassignTo(targetId: Long) {
            val id = accountId ?: return
            if (targetId == id) return
            finishRemoval { accountRepository.reassignAndDelete(id = id, targetId = targetId) }
        }

        /** Deletes the account and the movements in it. There is no undo behind this. */
        fun confirmDestructiveRemoval() {
            val id = accountId ?: return
            finishRemoval { accountRepository.deleteAccountWithTransactions(id) }
        }

        fun archive() {
            val id = accountId ?: return
            viewModelScope.launch {
                accountRepository.setArchived(id, archived = true)
                form.update { it.copy(finished = true) }
            }
        }

        fun restore() {
            val id = accountId ?: return
            viewModelScope.launch {
                accountRepository.setArchived(id, archived = false)
                form.update { it.copy(finished = true) }
            }
        }

        /** Runs one of the three removals and reports what happened, without throwing. */
        private fun finishRemoval(removal: suspend () -> Unit) {
            viewModelScope.launch {
                val removed = runCatching { removal() }
                form.update {
                    it.copy(
                        removalStep = RemovalStep.NONE,
                        writeFailed = removed.isFailure,
                        finished = removed.isSuccess,
                    )
                }
            }
        }

        /**
         * Where the movements could go: another account, still active, in the same currency.
         *
         * The currency filter is not fussiness. A movement keeps the currency it was
         * recorded in, and an account's balance sums its movements under the account's own
         * currency — so moving euros into a dollar account would not convert them, it would
         * silently make that balance a sum of two currencies (rule 2).
         */
        private suspend fun reassignTargets(id: Long): List<Account> {
            val currency = form.value.currency
            return accountRepository
                .observeAccounts()
                .first()
                .filter { it.id != id && it.currency == currency }
        }

        private fun isNameTaken(name: String): Boolean = name.trim().lowercase() in takenNames

        private suspend fun nextSortOrder(): Int =
            accountRepository
                .observeAccounts(includeArchived = true)
                .first()
                .maxOfOrNull { it.sortOrder + 1 } ?: 0

        private fun defaultCurrency(locale: Locale = Locale.getDefault()): String =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("EUR")

        private companion object {
            const val MAX_DIGITS = 12
        }
    }

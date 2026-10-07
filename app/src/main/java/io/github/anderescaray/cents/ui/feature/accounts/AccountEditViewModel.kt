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
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.deviceCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountEditUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    /**
     * The one currency the app works in, from the device locale. Not chosen and not
     * editable: see `DECISIONS.md` D2, and [deviceCurrency].
     */
    val currency: String = "EUR",
    val colorArgb: Int = AccountPalette.first(),
    /** Typed in euros, comma and all — the rules are [AmountEntry]'s. */
    val openingInput: String = AmountEntry.EMPTY,
    val openingNegative: Boolean = false,
    val archived: Boolean = false,
    val nameTaken: Boolean = false,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    /** Typed in euros, but stored as minor units like every amount in the app (rule 1). */
    val openingBalance: Money
        get() {
            val magnitude = AmountEntry.toMinorUnits(openingInput)
            return Money(if (openingNegative) -magnitude else magnitude, currency)
        }

    val canSave: Boolean get() = name.isNotBlank() && !nameTaken && !saving
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
        private val route = savedStateHandle.toRoute<Destination.AccountEdit>()

        private val accountId: Long? = route.accountId.takeIf { it != Destination.AccountEdit.NEW }

        // The currency is known without asking the database — it is the device's — so it is
        // set here rather than in load(). Reaching it through a suspending load left a
        // window where the form still held the fallback, and an account saved fast enough
        // was stored in it. With no picker left (D2), nothing could then correct it.
        private val form =
            MutableStateFlow(
                // What the device says money is called here. An existing account keeps whatever it was
                // created with, so an install that predates D2 holding a second currency is left as
                // it is rather than silently restated: the amounts in it are real.
                AccountEditUiState(isNew = accountId == null, type = route.type, currency = deviceCurrency()),
            )

        val uiState: StateFlow<AccountEditUiState> = form.asStateFlow()

        /**
         * The delete question, shared with the swipe on the list so both reach the same
         * sequence of confirmations rather than two implementations of it.
         */
        val removal =
            AccountRemoval(accountRepository, viewModelScope) {
                form.update { it.copy(finished = true) }
            }

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
                // Only the colour needs the database: it is picked to differ from the ones
                // already in use, which means knowing what those are.
                form.update { state ->
                    if (edited) state else state.copy(colorArgb = nextAccountColor(used.map { it.colorArgb }))
                }
                return
            }

            val account = accountRepository.findById(id) ?: return
            form.update { state ->
                state.copy(
                    isNew = false,
                    name = if (edited) state.name else account.name,
                    type = if (edited) state.type else account.type,
                    currency = if (edited) state.currency else account.currency,
                    colorArgb = if (edited) state.colorArgb else account.colorArgb,
                    openingInput =
                        if (edited) {
                            state.openingInput
                        } else {
                            AmountEntry.fromMinorUnits(account.openingBalance.minorUnits)
                        },
                    openingNegative =
                        if (edited) state.openingNegative else account.openingBalance.minorUnits < 0,
                    archived = account.archived,
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

        fun setColor(colorArgb: Int) {
            edited = true
            form.update { it.copy(colorArgb = colorArgb) }
        }

        fun appendDigit(digit: Char) {
            if (!digit.isDigit()) return
            edited = true
            form.update { it.copy(openingInput = AmountEntry.append(it.openingInput, digit)) }
        }

        /** The comma key. A second one, or a third digit behind it, simply does not go in. */
        fun appendDecimalSeparator() {
            edited = true
            form.update { it.copy(openingInput = AmountEntry.append(it.openingInput, AmountEntry.SEPARATOR)) }
        }

        fun deleteDigit() {
            edited = true
            form.update { it.copy(openingInput = AmountEntry.delete(it.openingInput)) }
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
         * Opens the question. The name comes from the form, so an alert raised over a
         * renamed-but-unsaved account names what is on screen.
         */
        fun requestRemoval() {
            val id = accountId ?: return
            removal.request(id, form.value.name)
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

        private fun isNameTaken(name: String): Boolean = name.trim().lowercase() in takenNames

        private suspend fun nextSortOrder(): Int =
            accountRepository
                .observeAccounts(includeArchived = true)
                .first()
                .maxOfOrNull { it.sortOrder + 1 } ?: 0
    }

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

/** What "delete" is able to do to this account, which is what the alert says. */
enum class AccountRemoval {
    /** No movement lives in it: it can go for good. */
    OUTRIGHT,

    /** It holds movements, so it can only leave the pickers with its history intact. */
    ARCHIVE,
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
    val confirmingRemoval: Boolean = false,
    val saving: Boolean = false,
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

    val removal: AccountRemoval
        get() = if (transactionCount == 0) AccountRemoval.OUTRIGHT else AccountRemoval.ARCHIVE
}

/**
 * Creating and editing one account.
 *
 * Like a category, an account that holds history cannot simply disappear: deleting it
 * would take its movements with it, so it is archived instead and the alert says which of
 * the two is about to happen.
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
            form.update { it.copy(name = name, nameTaken = isNameTaken(name)) }
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
                form.update { it.copy(saving = false, finished = written.isSuccess) }
            }
        }

        fun requestRemoval() {
            val id = accountId ?: return
            viewModelScope.launch {
                val count = accountRepository.countTransactions(id)
                form.update { it.copy(transactionCount = count, confirmingRemoval = true) }
            }
        }

        fun cancelRemoval() = form.update { it.copy(confirmingRemoval = false) }

        /** Deletes an empty account, archives one that holds movements. */
        fun confirmRemoval() {
            val id = accountId ?: return
            viewModelScope.launch {
                when (form.value.removal) {
                    AccountRemoval.OUTRIGHT -> accountRepository.deleteAccount(id)
                    AccountRemoval.ARCHIVE -> accountRepository.setArchived(id, archived = true)
                }
                form.update { it.copy(confirmingRemoval = false, finished = true) }
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

        private fun defaultCurrency(locale: Locale = Locale.getDefault()): String =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("EUR")

        private companion object {
            const val MAX_DIGITS = 12
        }
    }

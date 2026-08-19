package io.github.anderescaray.cents.ui.feature.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * State of the entry form.
 *
 * The amount is held as a string of digits meaning **minor units**: typing 1, 2, 5, 0 means
 * 12,50 €. That is how fast expense entry works on a phone — no decimal point to hit, no
 * locale-dependent parsing, and nothing that can round.
 */
data class AddTransactionUiState(
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountDigits: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val saved: Boolean = false,
) {
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == selectedAccountId }

    val currency: String get() = selectedAccount?.currency ?: "EUR"

    val amount: Money get() = Money(amountDigits.toLongOrNull() ?: 0L, currency)

    /** An amount of zero is not a transaction, and every movement needs an account. */
    val canSave: Boolean get() = amount.minorUnits > 0 && selectedAccountId != null
}

@HiltViewModel
class AddTransactionViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        clock: Clock,
    ) : ViewModel() {
        private val form = MutableStateFlow(AddTransactionUiState(date = LocalDate.now(clock)))

        val uiState: StateFlow<AddTransactionUiState> =
            combine(
                form,
                accountRepository.observeAccounts(),
                categoryRepository.observeAllCategories(),
            ) { state, accounts, categories ->
                // Archived categories keep classifying old transactions but are not offered
                // for new ones — that is the whole point of archiving instead of deleting.
                val kindCategories =
                    categories.filter { it.kind == state.kind.toCategoryKind() && !it.archived }
                state.copy(
                    accounts = accounts,
                    categories = kindCategories,
                    selectedAccountId = state.selectedAccountId ?: accounts.firstOrNull()?.id,
                    selectedCategoryId =
                        state.selectedCategoryId
                            ?.takeIf { id -> kindCategories.any { it.id == id } },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AddTransactionUiState(),
            )

        fun appendDigit(digit: Char) {
            if (!digit.isDigit()) return
            form.update { state ->
                val next = (state.amountDigits + digit).trimStart('0')
                if (next.length > MAX_DIGITS) state else state.copy(amountDigits = next)
            }
        }

        fun deleteDigit() {
            form.update { it.copy(amountDigits = it.amountDigits.dropLast(1)) }
        }

        fun setKind(kind: TransactionKind) {
            // The chosen category belongs to the other kind now, so it is cleared.
            form.update { it.copy(kind = kind, selectedCategoryId = null) }
        }

        fun selectAccount(id: Long) = form.update { it.copy(selectedAccountId = id) }

        fun selectCategory(id: Long?) = form.update { it.copy(selectedCategoryId = id) }

        fun setDate(date: LocalDate) = form.update { it.copy(date = date) }

        fun setNote(note: String) = form.update { it.copy(note = note) }

        fun save() {
            val state = uiState.value
            if (!state.canSave) return

            viewModelScope.launch {
                transactionRepository.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = requireNotNull(state.selectedAccountId),
                        categoryId = state.selectedCategoryId,
                        kind = state.kind,
                        amount = state.amount,
                        date = state.date,
                        note = state.note.takeIf { it.isNotBlank() },
                    ),
                )
                form.update { it.copy(saved = true) }
            }
        }

        private fun TransactionKind.toCategoryKind(): CategoryKind =
            when (this) {
                TransactionKind.INCOME -> CategoryKind.INCOME
                TransactionKind.EXPENSE -> CategoryKind.EXPENSE
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val MAX_DIGITS = 12
        }
    }

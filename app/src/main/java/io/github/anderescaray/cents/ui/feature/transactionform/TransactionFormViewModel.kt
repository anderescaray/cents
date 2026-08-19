package io.github.anderescaray.cents.ui.feature.transactionform

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * State of the movement form, adding or editing.
 *
 * The amount is held as a string of digits meaning **minor units**: typing 1, 2, 5, 0 means
 * 12,50 €. That is how fast expense entry works on a phone — no decimal point to hit, no
 * locale-dependent parsing, and nothing that can round.
 */
data class TransactionFormUiState(
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountDigits: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val isEditing: Boolean = false,
    val pickingDate: Boolean = false,
    val confirmingDeletion: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == selectedAccountId }

    val currency: String get() = selectedAccount?.currency ?: "EUR"

    val amount: Money get() = Money(amountDigits.toLongOrNull() ?: 0L, currency)

    /** An amount of zero is not a movement, and every movement needs an account. */
    val canSave: Boolean get() = amount.minorUnits > 0 && selectedAccountId != null && !saving
}

/**
 * One view model behind both adding and editing, because the fields are the same and two
 * copies of them would be two places for the rules about them to drift.
 *
 * The state is the form itself rather than a flow combined with `WhileSubscribed`: saving
 * and deleting read it to decide what to write, and a stale read there writes the wrong
 * thing to the database.
 */
@HiltViewModel
class TransactionFormViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val deletedTransactions: DeletedTransactionBuffer,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        savedStateHandle: SavedStateHandle,
        clock: Clock,
    ) : ViewModel() {
        private val transactionId: Long? =
            savedStateHandle
                .toRoute<Destination.TransactionForm>()
                .transactionId
                .takeIf { it != Destination.TransactionForm.NEW }

        private val form = MutableStateFlow(TransactionFormUiState(date = LocalDate.now(clock)))

        val uiState: StateFlow<TransactionFormUiState> = form.asStateFlow()

        /** Every category, so switching kind can re-filter without another query. */
        private var allCategories: List<Category> = emptyList()

        /** Until the categories have actually arrived, an empty list means "not yet". */
        private var categoriesLoaded = false

        /** The movement as it stands on disk, kept so a deletion can be undone whole. */
        private var stored: Transaction? = null

        init {
            viewModelScope.launch { load() }
            viewModelScope.launch {
                combine(
                    accountRepository.observeAccounts(),
                    categoryRepository.observeAllCategories(),
                ) { accounts, categories -> accounts to categories }
                    .collect { (accounts, categories) ->
                        allCategories = categories
                        categoriesLoaded = true
                        form.update { it.withOptions(accounts, categories) }
                    }
            }
        }

        private suspend fun load() {
            val id = transactionId ?: return
            val transaction = transactionRepository.findById(id) ?: return
            stored = transaction
            form.update { state ->
                state
                    .copy(
                        isEditing = true,
                        kind = transaction.kind,
                        amountDigits = transaction.amount.minorUnits.toString(),
                        selectedAccountId = transaction.accountId,
                        selectedCategoryId = transaction.categoryId,
                        date = transaction.date,
                        note = transaction.note.orEmpty(),
                    ).withOptions(state.accounts, allCategories)
            }
        }

        /**
         * Fills in what the pickers can offer, given what the form currently holds.
         *
         * The category this movement already carries stays on the list even once archived:
         * archiving keeps a category off *new* movements, and silently dropping it here
         * would uncategorize an old one on the next save.
         */
        private fun TransactionFormUiState.withOptions(
            accounts: List<Account>,
            categories: List<Category>,
        ): TransactionFormUiState {
            val offered =
                categories.filter { category ->
                    category.kind == kind.toCategoryKind() &&
                        (!category.archived || category.id == selectedCategoryId)
                }
            return copy(
                accounts = accounts,
                categories = offered,
                selectedAccountId = selectedAccountId ?: accounts.firstOrNull()?.id,
                // Only prune a selection once the real set is known: the form and the
                // category list load independently, and whichever arrives second must not
                // read the other's absence as "that category is gone".
                selectedCategoryId =
                    selectedCategoryId?.takeIf { id -> !categoriesLoaded || offered.any { it.id == id } },
            )
        }

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
            form.update {
                it.copy(kind = kind, selectedCategoryId = null).withOptions(it.accounts, allCategories)
            }
        }

        fun selectAccount(id: Long) = form.update { it.copy(selectedAccountId = id) }

        fun selectCategory(id: Long?) = form.update { it.copy(selectedCategoryId = id) }

        fun openDatePicker() = form.update { it.copy(pickingDate = true) }

        fun dismissDatePicker() = form.update { it.copy(pickingDate = false) }

        fun setDate(date: LocalDate) = form.update { it.copy(date = date, pickingDate = false) }

        fun setNote(note: String) = form.update { it.copy(note = note) }

        fun save() {
            val state = form.value
            if (!state.canSave) return
            // Marked before suspending: the button stays live until the write finishes, and
            // two quick taps would otherwise record the movement twice.
            form.update { it.copy(saving = true) }

            viewModelScope.launch {
                val movement =
                    Transaction(
                        id = transactionId ?: 0,
                        accountId = requireNotNull(state.selectedAccountId),
                        categoryId = state.selectedCategoryId,
                        kind = state.kind,
                        amount = state.amount,
                        date = state.date,
                        note = state.note.takeIf { it.isNotBlank() },
                    )
                if (transactionId == null) {
                    transactionRepository.addTransaction(movement)
                } else {
                    transactionRepository.updateTransaction(movement)
                }
                form.update { it.copy(saving = false, finished = true) }
            }
        }

        fun requestDeletion() = form.update { it.copy(confirmingDeletion = true) }

        fun cancelDeletion() = form.update { it.copy(confirmingDeletion = false) }

        /**
         * Removes the movement and hands it to [DeletedTransactionBuffer], so the list it
         * returns to can offer to put it back (rule 7).
         */
        fun confirmDeletion() {
            val id = transactionId ?: return
            viewModelScope.launch {
                val movement = stored ?: transactionRepository.findById(id)
                transactionRepository.deleteTransaction(id)
                if (movement != null) deletedTransactions.remember(movement)
                form.update { it.copy(confirmingDeletion = false, finished = true) }
            }
        }

        private companion object {
            const val MAX_DIGITS = 12
        }
    }

/** The categories a movement of this direction can be classified with. */
internal fun TransactionKind.toCategoryKind(): CategoryKind =
    when (this) {
        TransactionKind.INCOME -> CategoryKind.INCOME
        TransactionKind.EXPENSE -> CategoryKind.EXPENSE
    }

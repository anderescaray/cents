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

/**
 * State of the movement form, adding or editing.
 *
 * The amount is held as the text being typed, in **euros**: `1` `2` `,` `5` `0` means 12,50 €,
 * and `1` `2` alone means 12 €. [AmountEntry] owns every rule about what may go into that
 * string and turns it into the integer number of cents that is actually stored (rule 1).
 */
data class TransactionFormUiState(
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountInput: String = AmountEntry.EMPTY,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val note: String = "",
    /**
     * What this movement is denominated in: the account's currency for a new one, and the
     * currency it was recorded with for an existing one. Never inferred at save time — a
     * fallback there would silently rewrite what a movement is worth (rule 2).
     */
    val currency: String = DEFAULT_CURRENCY,
    val isEditing: Boolean = false,
    val pickingDate: Boolean = false,
    val confirmingDeletion: Boolean = false,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == selectedAccountId }

    val amount: Money get() = Money(AmountEntry.toMinorUnits(amountInput), currency)

    /** An amount of zero is not a movement, and every movement needs an account. */
    val canSave: Boolean get() = amount.minorUnits > 0 && selectedAccountId != null && !saving
}

/** A placeholder until an account is known; never what a movement is actually saved as. */
private const val DEFAULT_CURRENCY = "EUR"

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
        private val route = savedStateHandle.toRoute<Destination.TransactionForm>()

        private val transactionId: Long? =
            route.transactionId.takeIf { it != Destination.TransactionForm.NEW }

        private val today = LocalDate.now(clock)

        /**
         * Whether this screen is recording a movement or opening one that already exists.
         *
         * Deliberately not in [TransactionFormUiState], where `isEditing` means something
         * narrower and later: *the row has arrived from disk*. This is known in the
         * constructor and never changes, and the screen needs it on its first frame to decide
         * whether to open with the keypad up. Folding the two into one field made `isEditing`
         * true before the movement had loaded, which is a thing several tests reasonably
         * relied on it not being.
         */
        val isNewRecord: Boolean = transactionId == null

        private val form =
            MutableStateFlow(
                TransactionFormUiState(
                    // What was typed and chosen on the transfer form before the owner decided
                    // this was an ordinary movement after all. Both are ignored when opening
                    // one that already exists: [load] overwrites them with what was recorded.
                    kind = route.kind,
                    amountInput = route.amountInput,
                    date = today,
                    today = today,
                ),
            )

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
                        amountInput = AmountEntry.fromMinorUnits(transaction.amount.minorUnits),
                        selectedAccountId = transaction.accountId,
                        selectedCategoryId = transaction.categoryId,
                        date = transaction.date,
                        note = transaction.note.orEmpty(),
                        currency = transaction.amount.currency,
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
            // A movement with no account chosen yet takes the first one, and its currency
            // with it; an existing movement keeps the currency it was recorded in.
            val defaulted = if (selectedAccountId == null) accounts.firstOrNull() else null
            return copy(
                accounts = accounts,
                categories = offered,
                selectedAccountId = selectedAccountId ?: defaulted?.id,
                currency = defaulted?.currency ?: currency,
                // Only prune a selection once the real set is known: the form and the
                // category list load independently, and whichever arrives second must not
                // read the other's absence as "that category is gone".
                selectedCategoryId =
                    selectedCategoryId?.takeIf { id -> !categoriesLoaded || offered.any { it.id == id } },
            )
        }

        fun appendDigit(digit: Char) {
            if (!digit.isDigit()) return
            form.update { it.copy(amountInput = AmountEntry.append(it.amountInput, digit)) }
        }

        /** The comma key. A second one, or a third digit behind it, simply does not go in. */
        fun appendDecimalSeparator() {
            form.update { it.copy(amountInput = AmountEntry.append(it.amountInput, AmountEntry.SEPARATOR)) }
        }

        fun deleteDigit() {
            form.update { it.copy(amountInput = AmountEntry.delete(it.amountInput)) }
        }

        fun setKind(kind: TransactionKind) {
            // The chosen category belongs to the other kind now, so it is cleared.
            form.update {
                it.copy(kind = kind, selectedCategoryId = null).withOptions(it.accounts, allCategories)
            }
        }

        fun selectAccount(id: Long) =
            form.update { state ->
                // Choosing an account is choosing a currency: it is the same decision.
                val account = state.accounts.firstOrNull { it.id == id }
                state.copy(selectedAccountId = id, currency = account?.currency ?: state.currency)
            }

        fun selectCategory(id: Long?) = form.update { it.copy(selectedCategoryId = id) }

        fun openDatePicker() = form.update { it.copy(pickingDate = true) }

        fun dismissDatePicker() = form.update { it.copy(pickingDate = false) }

        fun setDate(date: LocalDate) = form.update { it.copy(date = date, pickingDate = false) }

        fun setNote(note: String) = form.update { it.copy(note = note, writeFailed = false) }

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
                val written =
                    runCatching {
                        if (transactionId == null) {
                            transactionRepository.addTransaction(movement)
                        } else {
                            transactionRepository.updateTransaction(movement)
                        }
                    }
                // A write that fails leaves the form open and says so, rather than taking
                // the app down or leaving Save greyed out with nothing explaining why.
                form.update {
                    it.copy(
                        saving = false,
                        writeFailed = written.isFailure,
                        finished = written.isSuccess,
                    )
                }
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
                val removed = runCatching { transactionRepository.deleteTransaction(id) }
                if (removed.isSuccess && movement != null) deletedTransactions.remember(movement)
                form.update {
                    it.copy(
                        confirmingDeletion = false,
                        writeFailed = removed.isFailure,
                        finished = removed.isSuccess,
                    )
                }
            }
        }
    }

/** The categories a movement of this direction can be classified with. */
internal fun TransactionKind.toCategoryKind(): CategoryKind =
    when (this) {
        TransactionKind.INCOME -> CategoryKind.INCOME
        TransactionKind.EXPENSE -> CategoryKind.EXPENSE
    }

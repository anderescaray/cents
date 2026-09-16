package io.github.anderescaray.cents.ui.feature.transferform

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transfer
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.TransferRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.AmountEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Which of the two numbers the keypad is currently typing into. */
enum class TransferField {
    AMOUNT,
    FEE,
}

/**
 * State of the transfer form, recording one or editing one.
 *
 * Both numbers are typed in euros with the comma asking for cents, exactly as the movement
 * keypad works ([AmountEntry]) — there is one set of rules in this app for what a half-typed
 * number means, and a second field is not a reason to invent another.
 */
@Immutable
data class TransferFormUiState(
    val amountInput: String = AmountEntry.EMPTY,
    /** What the move cost. Empty is the usual case, and reads as zero. */
    val feeInput: String = AmountEntry.EMPTY,
    val accounts: List<Account> = emptyList(),
    /**
     * Whether [accounts] has actually been read, which an empty list alone does not say.
     *
     * Without it the form paints *"create a second account"* for the frame before the query
     * comes back — telling a two-account install it has one, on the way to every transfer.
     */
    val accountsLoaded: Boolean = false,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val typing: TransferField = TransferField.AMOUNT,
    val date: LocalDate = LocalDate.EPOCH,
    val today: LocalDate = LocalDate.EPOCH,
    val note: String = "",
    val currency: String = DEFAULT_CURRENCY,
    val isEditing: Boolean = false,
    val pickingDate: Boolean = false,
    val confirmingDeletion: Boolean = false,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    val amount: Money get() = Money(AmountEntry.toMinorUnits(amountInput), currency)

    val fee: Money get() = Money(AmountEntry.toMinorUnits(feeInput), currency)

    /** What actually leaves the source account: the amount plus whatever the move costs. */
    val amountLeaving: Money get() = amount + fee

    val fromAccount: Account? get() = accounts.firstOrNull { it.id == fromAccountId }

    /**
     * Where the money can go: any other account in the same currency.
     *
     * The currency filter is the same one the account-deletion flow applies to reassignment,
     * for the same reason — a movement keeps the currency it was recorded in, and an account's
     * balance sums its movements under the account's own currency, so sending euros into a
     * dollar account would not convert them, it would make that balance a sum of two
     * currencies (rule 2). The app works in one currency (D2), so in practice this excludes
     * nothing; it is the guard rail that keeps an install predating D2 honest.
     */
    val destinations: List<Account>
        get() {
            val source = fromAccount ?: return emptyList()
            return accounts.filter { it.id != source.id && it.currency == source.currency }
        }

    /** A transfer needs two accounts to exist at all, which a fresh install does not have. */
    val needsSecondAccount: Boolean get() = accountsLoaded && accounts.size < 2

    /**
     * Whether the kind can still be changed from here.
     *
     * Only while recording a new one. Leaving for the movement form would leave a transfer that
     * already exists standing — both its legs — and add a third row on top of them.
     */
    val canChangeKind: Boolean get() = !isEditing

    /**
     * Zero is not a transfer, and neither is money sent to the account it is already in.
     * The fee may be anything, zero included: most transfers cost nothing.
     */
    val canSave: Boolean
        get() =
            amount.minorUnits > 0 &&
                fromAccountId != null &&
                toAccountId != null &&
                fromAccountId != toAccountId &&
                !saving
}

/** A placeholder until an account is known; never what a transfer is actually saved as. */
private const val DEFAULT_CURRENCY = "EUR"

/**
 * Recording money moved between two of the owner's own accounts.
 *
 * A separate screen from the movement form rather than a third mode inside it: a transfer has
 * two accounts and no category, so sharing one form would mean every section of it asking
 * which kind it is being rendered for. What the two do share is the control at the top, so
 * they read as one thing with three settings — choosing *Expense* or *Income* here leaves for
 * the other screen, carrying whatever amount has been typed so far.
 *
 * The state is the form itself rather than a flow with `WhileSubscribed`: saving and deleting
 * read it to decide what to write, and a stale read there writes the wrong thing.
 */
@HiltViewModel
class TransferFormViewModel
    @Inject
    constructor(
        private val transferRepository: TransferRepository,
        accountRepository: AccountRepository,
        savedStateHandle: SavedStateHandle,
        clock: Clock,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<Destination.TransferForm>()

        private val transferId: Long? = route.transferId.takeIf { it != Destination.TransferForm.NEW }

        private val today = LocalDate.now(clock)

        /** Known in the constructor: the screen opens with the keypad up only for a new one. */
        val isNewRecord: Boolean = transferId == null

        private val form =
            MutableStateFlow(
                TransferFormUiState(
                    // An amount typed on the movement form before the owner realised this was
                    // a transfer. Losing it would make the segmented control a punishment for
                    // changing your mind.
                    amountInput = route.amountInput,
                    date = today,
                    today = today,
                ),
            )

        val uiState: StateFlow<TransferFormUiState> = form.asStateFlow()

        /**
         * Every account, archived ones included, kept unfiltered.
         *
         * What reaches the state is the *pruned* list, so re-deriving from `state.accounts`
         * would filter an already-filtered list — and an archived account dropped before the
         * transfer that uses it had loaded could never come back. It is the same reason
         * `TransactionFormViewModel` keeps `allCategories` beside the offered ones.
         */
        private var allAccounts: List<Account> = emptyList()

        init {
            viewModelScope.launch { load() }
            viewModelScope.launch {
                // Archived accounts are included and then filtered, so that editing a transfer
                // into an account that was archived afterwards still shows where the money
                // went instead of quietly emptying the row.
                accountRepository.observeAccounts(includeArchived = true).collect { accounts ->
                    allAccounts = accounts
                    // The flag goes on before the list is derived, not after: `withAccounts`
                    // reads it to decide whether a chosen destination may be pruned, and on
                    // this emission the answer is known.
                    form.update { it.copy(accountsLoaded = true).withAccounts(accounts) }
                }
            }
        }

        private suspend fun load() {
            val id = transferId ?: return
            val transfer = transferRepository.findTransfer(id) ?: return
            form.update { state ->
                state
                    .copy(
                        isEditing = true,
                        amountInput = AmountEntry.fromMinorUnits(transfer.amount.minorUnits),
                        feeInput =
                            AmountEntry
                                .fromMinorUnits(transfer.fee.minorUnits)
                                .takeIf { transfer.fee.minorUnits != 0L }
                                ?: AmountEntry.EMPTY,
                        fromAccountId = transfer.fromAccountId,
                        toAccountId = transfer.toAccountId,
                        date = transfer.date,
                        note = transfer.note.orEmpty(),
                        currency = transfer.amount.currency,
                        // Whichever of this and the accounts flow lands second re-derives from
                        // the full list, so the order they arrive in cannot decide whether an
                        // archived account is still offered.
                    ).withAccounts(allAccounts)
            }
        }

        /**
         * What the two pickers can offer, given what the form holds.
         *
         * An archived account stays on the list while it is one end of *this* transfer, and
         * is dropped otherwise: archiving means stop offering it, not pretend the money never
         * went there. A destination that no longer qualifies — because the source changed to
         * the same account, or to one in another currency — is let go rather than left
         * selected under a picker that no longer shows it.
         */
        private fun TransferFormUiState.withAccounts(all: List<Account>): TransferFormUiState {
            val offered =
                all.filter { account ->
                    !account.archived || account.id == fromAccountId || account.id == toAccountId
                }
            val source = fromAccountId ?: offered.firstOrNull()?.id
            val sourceAccount = offered.firstOrNull { it.id == source }
            val destination =
                toAccountId?.takeIf { id ->
                    !accountsLoaded ||
                        offered.any { it.id == id && it.id != source && it.currency == sourceAccount?.currency }
                }
            return copy(
                accounts = offered,
                fromAccountId = source,
                toAccountId = destination,
                // Choosing an account is choosing a currency: it is the same decision. An
                // existing transfer keeps the one it was recorded in.
                currency = if (isEditing) currency else sourceAccount?.currency ?: currency,
            )
        }

        fun appendDigit(digit: Char) {
            if (!digit.isDigit()) return
            form.update { it.onTyped { input -> AmountEntry.append(input, digit) } }
        }

        /** The comma key. A second one, or a third digit behind it, simply does not go in. */
        fun appendDecimalSeparator() {
            form.update { it.onTyped { input -> AmountEntry.append(input, AmountEntry.SEPARATOR) } }
        }

        fun deleteDigit() {
            form.update { it.onTyped(AmountEntry::delete) }
        }

        /** Sends the keystroke to whichever of the two numbers is being typed into. */
        private fun TransferFormUiState.onTyped(edit: (String) -> String): TransferFormUiState =
            when (typing) {
                TransferField.AMOUNT -> copy(amountInput = edit(amountInput))
                TransferField.FEE -> copy(feeInput = edit(feeInput))
            }

        fun typeInto(field: TransferField) = form.update { it.copy(typing = field) }

        fun selectFrom(id: Long) =
            form.update { state ->
                val account = state.accounts.firstOrNull { it.id == id }
                state
                    .copy(fromAccountId = id, currency = account?.currency ?: state.currency)
                    .withAccounts(allAccounts)
            }

        fun selectTo(id: Long) = form.update { it.copy(toAccountId = id) }

        /**
         * Swaps the two ends. It is one tap for the mistake this form makes easiest to make,
         * and it keeps everything else typed.
         */
        fun swapAccounts() =
            form.update { state ->
                val destination = state.toAccountId ?: return@update state
                state.copy(fromAccountId = destination, toAccountId = state.fromAccountId)
            }

        fun openDatePicker() = form.update { it.copy(pickingDate = true) }

        fun dismissDatePicker() = form.update { it.copy(pickingDate = false) }

        fun setDate(date: LocalDate) = form.update { it.copy(date = date, pickingDate = false) }

        fun setNote(note: String) = form.update { it.copy(note = note, writeFailed = false) }

        fun save() {
            val state = form.value
            if (!state.canSave) return
            // Marked before suspending: the button stays live until the write finishes, and
            // two quick taps would otherwise record the transfer twice.
            form.update { it.copy(saving = true) }

            viewModelScope.launch {
                val transfer =
                    Transfer(
                        id = transferId ?: Transfer.NEW,
                        fromAccountId = requireNotNull(state.fromAccountId),
                        toAccountId = requireNotNull(state.toAccountId),
                        amount = state.amount,
                        fee = state.fee,
                        date = state.date,
                        note = state.note.takeIf { it.isNotBlank() },
                    )
                val written = runCatching { transferRepository.saveTransfer(transfer) }
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
         * Removes both legs.
         *
         * There is no undo behind it, unlike a movement's: the buffer the list offers Undo
         * from holds one movement, and half a transfer put back is money arriving from
         * nowhere. So the confirmation is the whole of the protection (rule 7), and it says
         * that both sides go.
         */
        fun confirmDeletion() {
            val id = transferId ?: return
            viewModelScope.launch {
                val removed = runCatching { transferRepository.deleteTransfer(id) }
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

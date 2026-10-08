package io.github.anderescaray.cents.ui.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.BalanceAdjustment
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.BalanceRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.undo.DeletedTransaction
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * One day inside a single account: its movements, and any balance corrections dated that day,
 * which are listed after them because they are not movements (`BalanceAdjustment`).
 */
data class AccountDay(
    val date: LocalDate,
    val items: List<TransactionDetails>,
    val corrections: List<BalanceAdjustment> = emptyList(),
)

/** A correction the owner asked to remove, and what the balance would be without it. */
data class CorrectionRemoval(
    val correction: BalanceAdjustment,
    val balanceAfter: Money,
)

/** Where removing a correction from this screen stands. */
data class CorrectionState(
    /** The id waiting on the confirmation every removal gets (rule 7). */
    val askingId: Long? = null,
    /** Just removed, and still offered back. */
    val removed: BalanceAdjustment? = null,
    /** The removal or its undo was refused, and the owner has not acknowledged it yet. */
    val failed: Boolean = false,
)

data class AccountDetailUiState(
    val account: AccountWithBalance? = null,
    val monthSummary: PeriodSummary? = null,
    val days: List<AccountDay> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    /** A movement or transfer just deleted from this screen's form, still offered back. */
    val undoableDeletion: DeletedTransaction? = null,
    val corrections: CorrectionState = CorrectionState(),
) {
    /**
     * The removal being asked about, with the balance it would leave worked out here rather than
     * in the alert. Null when nothing is asked, or the correction is no longer listed.
     */
    val removal: CorrectionRemoval? =
        corrections.askingId?.let { id ->
            val correction = days.firstNotNullOfOrNull { day -> day.corrections.find { it.id == id } }
            val balance = account?.balance
            if (correction != null && balance != null) {
                CorrectionRemoval(correction, balanceAfter = balance - correction.amount)
            } else {
                null
            }
        }

    /** The correction offered back, unless a movement's undo is already showing. */
    val undoableCorrection: BalanceAdjustment? = corrections.removed.takeIf { undoableDeletion == null }

    val isEmpty: Boolean get() = !isLoading && days.isEmpty()

    /**
     * The account this screen is about no longer exists — deleted from the editor this
     * screen pushed to, most likely. The screen leaves rather than sitting there with a
     * fallback title and no balance, waiting to be backed out of by hand.
     *
     * [isLoading] is only false once all three flows have emitted, so this cannot fire on
     * the way in, while the account is merely not read yet.
     */
    val isGone: Boolean get() = !isLoading && account == null
}

/**
 * One account on its own: what is in it, what it did this month, and every movement that
 * produced that balance — so the number at the top can always be traced to the rows below.
 */
@HiltViewModel
class AccountDetailViewModel
    @Inject
    constructor(
        accountRepository: AccountRepository,
        private val transactionRepository: TransactionRepository,
        private val balanceRepository: BalanceRepository,
        private val deletedTransactions: DeletedTransactionBuffer,
        savedStateHandle: SavedStateHandle,
        today: Today,
    ) : ViewModel() {
        private val accountId: Long = savedStateHandle.toRoute<Destination.AccountDetail>().accountId

        private val handle = savedStateHandle

        /**
         * The correction being asked about, kept in the saved state so the question survives the
         * screen being rotated or the process being reclaimed while the alert is up.
         */
        private val askingId = savedStateHandle.getStateFlow(ASKING_KEY, NOTHING_ASKED)

        private val removedCorrection = MutableStateFlow<BalanceAdjustment?>(null)
        private val correctionFailed = MutableStateFlow(false)
        private var undoExpiry: Job? = null

        private val corrections: Flow<CorrectionState> =
            combine(askingId, removedCorrection, correctionFailed) { asking, removed, failed ->
                CorrectionState(askingId = asking.takeIf { it != NOTHING_ASKED }, removed = removed, failed = failed)
            }

        private val account: Flow<AccountWithBalance?> =
            accountRepository.observeAccountWithBalance(accountId)

        /**
         * The month's totals, re-queried only when the currency they are expressed in
         * changes, or the day does — not on every movement, which is what watching the whole
         * account would do, since its balance moves with each one.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val monthSummary: Flow<PeriodSummary?> =
            combine(
                account.map { it?.account?.currency }.distinctUntilChanged(),
                today.date,
            ) { currency, day -> currency to day }
                .flatMapLatest { (currency, day) ->
                    if (currency == null) {
                        flowOf(null)
                    } else {
                        transactionRepository.observeAccountSummary(
                            accountId = accountId,
                            from = day.withDayOfMonth(1),
                            to = day.withDayOfMonth(day.lengthOfMonth()),
                            currency = currency,
                        )
                    }
                }

        /**
         * The rows behind the balance, by day: every movement, and every correction. Without the
         * corrections the number at the top could no longer be traced to the rows below it.
         */
        private val days: Flow<List<AccountDay>> =
            combine(
                transactionRepository.observeAccountTransactionDetails(accountId),
                balanceRepository.observeAdjustments(accountId),
            ) { movements, corrections ->
                val movementsByDay = movements.groupBy { it.transaction.date }
                val correctionsByDay = corrections.groupBy { it.date }
                (movementsByDay.keys + correctionsByDay.keys)
                    .map { date ->
                        AccountDay(
                            date = date,
                            items = movementsByDay[date].orEmpty(),
                            corrections = correctionsByDay[date].orEmpty(),
                        )
                    }.sortedByDescending { it.date }
            }

        private val content: Flow<AccountDetailUiState> =
            combine(
                account,
                days,
                monthSummary,
                deletedTransactions.deleted,
                today.date,
            ) { account, days, summary, deleted, day ->
                AccountDetailUiState(
                    account = account,
                    monthSummary = summary,
                    days = days,
                    today = day,
                    isLoading = false,
                    undoableDeletion = deleted,
                )
            }

        val uiState: StateFlow<AccountDetailUiState> =
            combine(content, corrections) { state, corrections ->
                state.copy(corrections = corrections)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AccountDetailUiState(),
            )

        /**
         * Puts a deleted movement or transfer back, with the row ids it had — the same undo the
         * list offers, because a movement deleted from here pops back here rather than to the list.
         */
        fun undoDeletion() {
            val deleted = deletedTransactions.take() ?: return
            viewModelScope.launch { transactionRepository.restore(deleted.movements) }
        }

        fun dismissUndo() = deletedTransactions.clear()

        /** A tap on a correction: ask before it goes. */
        fun askToRemoveCorrection(id: Long) {
            handle[ASKING_KEY] = id
        }

        fun cancelCorrectionRemoval() {
            handle[ASKING_KEY] = NOTHING_ASKED
        }

        /**
         * Removes the correction asked about. The balance goes back to what it was without it, and
         * the correction is offered back for a moment, like a deleted movement (rule 7).
         */
        fun confirmCorrectionRemoval() {
            val id = askingId.value.takeIf { it != NOTHING_ASKED } ?: return
            handle[ASKING_KEY] = NOTHING_ASKED
            viewModelScope.launch {
                val removed = attempt { balanceRepository.deleteAdjustment(id) }
                if (removed.isSuccess) {
                    removed.getOrNull()?.let(::offerBack)
                } else {
                    correctionFailed.value = true
                }
            }
        }

        /** Puts the removed correction back, with the id it had. */
        fun undoCorrectionRemoval() {
            val correction = removedCorrection.value ?: return
            undoExpiry?.cancel()
            removedCorrection.value = null
            viewModelScope.launch {
                // Refused only when the account went in the meantime, and then there is nothing
                // to put it back into: said, rather than thrown out of the scope.
                if (attempt { balanceRepository.restoreAdjustment(correction) }.isFailure) {
                    correctionFailed.value = true
                }
            }
        }

        fun dismissCorrectionFailure() {
            correctionFailed.value = false
        }

        private fun offerBack(correction: BalanceAdjustment) {
            undoExpiry?.cancel()
            removedCorrection.value = correction
            undoExpiry =
                viewModelScope.launch {
                    delay(DeletedTransactionBuffer.UNDO_WINDOW_MS)
                    removedCorrection.value = null
                }
        }

        /** A write that can be refused, without swallowing the cancellation of the screen's scope. */
        private suspend fun <T> attempt(write: suspend () -> T): Result<T> =
            try {
                Result.success(write())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") refused: Exception,
            ) {
                Result.failure(refused)
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val ASKING_KEY = "removingCorrection"
            const val NOTHING_ASKED = -1L
        }
    }

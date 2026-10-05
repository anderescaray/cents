package io.github.anderescaray.cents.ui.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.BalanceAdjustment
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.BalanceRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.undo.DeletedTransaction
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

data class AccountDetailUiState(
    val account: AccountWithBalance? = null,
    val monthSummary: PeriodSummary? = null,
    val days: List<AccountDay> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    /** A movement or transfer just deleted from this screen's form, still offered back. */
    val undoableDeletion: DeletedTransaction? = null,
) {
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

        val uiState: StateFlow<AccountDetailUiState> =
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

        /** Asked for behind a confirmation: the balance goes back to what it was without it. */
        fun removeCorrection(id: Long) {
            viewModelScope.launch { balanceRepository.deleteAdjustment(id) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

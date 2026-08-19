package io.github.anderescaray.cents.ui.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** One day's worth of movements, which is how the list is grouped. */
data class TransactionDay(
    val date: LocalDate,
    val items: List<TransactionDetails>,
)

data class TransactionsUiState(
    val days: List<TransactionDay> = emptyList(),
    val monthSummary: PeriodSummary? = null,
    val isLoading: Boolean = true,
    /** The day the labels are relative to, from the injected clock rather than the device. */
    val today: LocalDate = LocalDate.now(),
    /** A movement just deleted on the form, still restorable from here (rule 7). */
    val undoableDeletion: Transaction? = null,
) {
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
}

@HiltViewModel
class TransactionsViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val deletedTransactions: DeletedTransactionBuffer,
        accountRepository: AccountRepository,
        clock: Clock,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<TransactionsUiState> =
            combine(
                transactionRepository.observeTransactions(),
                accountRepository.observeAccounts().flatMapLatest { accounts ->
                    val currency = accounts.firstOrNull()?.currency
                    if (currency == null) {
                        flowOf(null)
                    } else {
                        val today = LocalDate.now(clock)
                        transactionRepository.observeSummary(
                            from = today.withDayOfMonth(1),
                            to = today.withDayOfMonth(today.lengthOfMonth()),
                            currency = currency,
                        )
                    }
                },
                deletedTransactions.deleted,
            ) { transactions, summary, deleted ->
                TransactionsUiState(
                    days =
                        transactions
                            .groupBy { it.transaction.date }
                            .map { (date, items) -> TransactionDay(date, items) }
                            .sortedByDescending { it.date },
                    monthSummary = summary,
                    isLoading = false,
                    today = LocalDate.now(clock),
                    undoableDeletion = deleted,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TransactionsUiState(),
            )

        /**
         * Puts a deleted movement back, with the row id it had: an undo that returns a
         * different movement is not an undo.
         */
        fun undoDeletion() {
            val movement = deletedTransactions.take() ?: return
            viewModelScope.launch { transactionRepository.addTransaction(movement) }
        }

        fun dismissUndo() = deletedTransactions.clear()

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** Convenience for screens: the sum of a day's movements, signed. */
fun TransactionDay.total(currency: String): Money =
    items.fold(Money.zero(currency)) { acc, details -> acc + details.transaction.signedAmount }

package io.github.anderescaray.cents.ui.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
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
    /** What the list is currently narrowed to. [TransactionFilter.None] is everything. */
    val filter: TransactionFilter = TransactionFilter.None,
    /** How many movements the filter matched, which is half of any answer it gives. */
    val matchCount: Int = 0,
    /**
     * Income and expenses across the matched movements, and null when they do not share a
     * currency — the same refusal the accounts list makes rather than adding up a rate the
     * app does not have.
     */
    val matchSummary: PeriodSummary? = null,
) {
    val isNarrowed: Boolean get() = filter.isNarrowed

    /** Nothing to show. Which of the two things that means is [isNarrowed]. */
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
}

@HiltViewModel
class TransactionsViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val deletedTransactions: DeletedTransactionBuffer,
        private val filters: TransactionFilterStore,
        accountRepository: AccountRepository,
        clock: Clock,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<TransactionsUiState> =
            combine(
                // The filter and the rows it produced travel together. Combined separately
                // they can disagree for a frame — a new filter beside the previous list —
                // and the summary underneath would then be a total of the wrong movements.
                filters.filter.flatMapLatest { filter ->
                    transactionRepository.observeTransactions(filter).map { filter to it }
                },
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
            ) { (filter, transactions), summary, deleted ->
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
                    filter = filter,
                    matchCount = transactions.size,
                    matchSummary = transactions.summarise(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TransactionsUiState(),
            )

        fun setQuery(query: String) = filters.setQuery(query)

        fun clearFilter() = filters.clear()

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

/**
 * What the matched movements add up to, summed here rather than by a second query.
 *
 * The rows are already in memory — they are the ones on screen — so a `GROUP BY` would be a
 * second read of the same thing, and one that could disagree with the list it sits above if
 * a write landed between the two. The rule about aggregating in SQL is about not walking
 * thousands of rows the UI never asked for; these are exactly the rows it asked for.
 *
 * Null when the matched movements do not share a currency: adding euros to dollars needs a
 * rate the app does not have (rule 2, `DECISIONS.md` D2).
 */
private fun List<TransactionDetails>.summarise(): PeriodSummary? {
    val currency = map { it.transaction.amount.currency }.distinct().singleOrNull() ?: return null
    val zero = Money.zero(currency)
    return PeriodSummary(
        income =
            filter { it.transaction.kind == TransactionKind.INCOME }
                .fold(zero) { total, details -> total + details.transaction.amount },
        expenses =
            filter { it.transaction.kind == TransactionKind.EXPENSE }
                .fold(zero) { total, details -> total + details.transaction.amount },
    )
}

/** Convenience for screens: the sum of a day's movements, signed. */
fun TransactionDay.total(currency: String): Money =
    items.fold(Money.zero(currency)) { acc, details -> acc + details.transaction.signedAmount }

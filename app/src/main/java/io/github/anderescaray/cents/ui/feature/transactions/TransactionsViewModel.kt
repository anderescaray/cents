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
import io.github.anderescaray.cents.ui.undo.DeletedTransaction
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    /** The day the labels are relative to, from [io.github.anderescaray.cents.ui.util.Today]. */
    val today: LocalDate = LocalDate.now(),
    /** A movement or transfer just deleted on the form, still restorable from here (rule 7). */
    val undoableDeletion: DeletedTransaction? = null,
    /** What the list is currently narrowed to. [TransactionFilter.None] is everything. */
    val filter: TransactionFilter = TransactionFilter.None,
    /**
     * What is in the search field right now, which is ahead of [filter] by up to one
     * debounce. The field is fed from here rather than from the applied filter: a
     * value-based text field showing text that has been to the database and back drops
     * characters as fast as they are typed.
     */
    val query: String = "",
    /** How many movements the filter matched, which is half of any answer it gives. */
    val matchCount: Int = 0,
    /**
     * Income and expenses across the matched movements, and null when they do not share a
     * currency — the same refusal the accounts list makes rather than adding up a rate the
     * app does not have.
     */
    val matchSummary: PeriodSummary? = null,
    /** How many movements wait in the uncategorized inbox, so the list can point at it. */
    val uncategorizedCount: Int = 0,
) {
    val isNarrowed: Boolean get() = filter.isNarrowed

    /** Nothing to show. Which of the two things that means is [isNarrowed]. */
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class TransactionsViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val deletedTransactions: DeletedTransactionBuffer,
        private val filters: TransactionFilterStore,
        accountRepository: AccountRepository,
        today: Today,
    ) : ViewModel() {
        /**
         * The text as typed, updated on the keystroke and only pushed to the store once the
         * typing pauses. Without the pause, every character cancels a running query and
         * starts a full-table LIKE over the whole ledger.
         */
        private val typed = MutableStateFlow(filters.filter.value.query)

        init {
            viewModelScope.launch {
                typed.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged().collect(filters::setQuery)
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        val uiState: StateFlow<TransactionsUiState> =
            combine(
                // The filter and the rows it produced travel together. Combined separately
                // they can disagree for a frame — a new filter beside the previous list —
                // and the summary underneath would then be a total of the wrong movements.
                filters.filter.flatMapLatest { filter ->
                    transactionRepository.observeTransactions(filter).map { filter to it }
                },
                // Re-asked when the day changes as well as the currency, so the summary moves to
                // the new month at midnight on the first rather than staying on the old one.
                combine(
                    accountRepository.observeAccounts().map { it.firstOrNull()?.currency }.distinctUntilChanged(),
                    today.date,
                ) { currency, day -> currency to day }.flatMapLatest { (currency, day) ->
                    if (currency == null) {
                        flowOf(null)
                    } else {
                        transactionRepository.observeSummary(
                            from = day.withDayOfMonth(1),
                            to = day.withDayOfMonth(day.lengthOfMonth()),
                            currency = currency,
                        )
                    }
                },
                deletedTransactions.deleted,
                typed,
                today.date,
            ) { (filter, transactions), summary, deleted, query, day ->
                TransactionsUiState(
                    days =
                        transactions
                            .groupBy { it.transaction.date }
                            .map { (date, items) -> TransactionDay(date, items) }
                            .sortedByDescending { it.date },
                    monthSummary = summary,
                    isLoading = false,
                    today = day,
                    undoableDeletion = deleted,
                    filter = filter,
                    query = query,
                    matchCount = transactions.size,
                    matchSummary = transactions.summarise(),
                )
            }.combine(transactionRepository.observeUncategorizedCount()) { state, waiting ->
                state.copy(uncategorizedCount = waiting)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TransactionsUiState(),
            )

        fun setQuery(query: String) {
            typed.value = query
        }

        fun clearFilter() {
            typed.value = ""
            filters.clear()
        }

        /**
         * Puts a deleted movement or transfer back, with the row ids it had: an undo that returns a
         * different movement is not an undo.
         */
        fun undoDeletion() {
            val deleted = deletedTransactions.take() ?: return
            viewModelScope.launch { transactionRepository.restore(deleted.movements) }
        }

        fun dismissUndo() = deletedTransactions.clear()

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L

            /** Long enough to cover typing, short enough that the list feels immediate. */
            const val SEARCH_DEBOUNCE_MS = 250L
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
 *
 * **Transfer legs and the cash of a trade are left out**, the same way every report query leaves
 * them out: money moved between the owner's own accounts, or turned into shares, was neither
 * earned nor spent. Leaving out only the legs made a month opened from the income-and-expenses
 * chart add up to more than its column whenever shares were bought. They stay *on* the list —
 * they happened, and they have to be findable — so a filtered list can legitimately show rows
 * this total does not count. The currency is read from every row, those included, because a
 * total the app refuses to add up is a fact about what was matched rather than about what was
 * counted.
 */
private fun List<TransactionDetails>.summarise(): PeriodSummary? {
    val currency = map { it.transaction.amount.currency }.distinct().singleOrNull() ?: return null
    val zero = Money.zero(currency)
    val movements = filterNot { it.transaction.movesNoIncomeOrExpense }
    return PeriodSummary(
        income =
            movements
                .filter { it.transaction.kind == TransactionKind.INCOME }
                .fold(zero) { total, details -> total + details.transaction.amount },
        expenses =
            movements
                .filter { it.transaction.kind == TransactionKind.EXPENSE }
                .fold(zero) { total, details -> total + details.transaction.amount },
    )
}

/** Convenience for screens: the sum of a day's movements, signed. */
fun TransactionDay.total(currency: String): Money =
    items.fold(Money.zero(currency)) { acc, details -> acc + details.transaction.signedAmount }

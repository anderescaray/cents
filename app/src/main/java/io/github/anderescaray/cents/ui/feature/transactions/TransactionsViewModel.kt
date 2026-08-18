package io.github.anderescaray.cents.ui.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
) {
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
}

@HiltViewModel
class TransactionsViewModel
    @Inject
    constructor(
        transactionRepository: TransactionRepository,
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
            ) { transactions, summary ->
                TransactionsUiState(
                    days =
                        transactions
                            .groupBy { it.transaction.date }
                            .map { (date, items) -> TransactionDay(date, items) }
                            .sortedByDescending { it.date },
                    monthSummary = summary,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TransactionsUiState(),
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** Convenience for screens: the sum of a day's movements, signed. */
fun TransactionDay.total(currency: String): Money =
    items.fold(Money.zero(currency)) { acc, details -> acc + details.transaction.signedAmount }

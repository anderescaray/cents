package io.github.anderescaray.cents.ui.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** One day of movements inside a single account. */
data class AccountDay(
    val date: LocalDate,
    val items: List<Transaction>,
)

data class AccountDetailUiState(
    val account: AccountWithBalance? = null,
    val monthSummary: PeriodSummary? = null,
    val days: List<AccountDay> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
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
        transactionRepository: TransactionRepository,
        savedStateHandle: SavedStateHandle,
        clock: Clock,
    ) : ViewModel() {
        private val accountId: Long = savedStateHandle.toRoute<Destination.AccountDetail>().accountId

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<AccountDetailUiState> =
            combine(
                accountRepository.observeAccountWithBalance(accountId),
                transactionRepository.observeTransactionsForAccount(accountId),
                accountRepository.observeAccountWithBalance(accountId).flatMapLatest { account ->
                    if (account == null) {
                        flowOf(null)
                    } else {
                        val today = LocalDate.now(clock)
                        transactionRepository.observeAccountSummary(
                            accountId = accountId,
                            from = today.withDayOfMonth(1),
                            to = today.withDayOfMonth(today.lengthOfMonth()),
                            currency = account.account.currency,
                        )
                    }
                },
            ) { account, movements, summary ->
                AccountDetailUiState(
                    account = account,
                    monthSummary = summary,
                    days =
                        movements
                            .groupBy { it.date }
                            .map { (date, items) -> AccountDay(date, items) }
                            .sortedByDescending { it.date },
                    today = LocalDate.now(clock),
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AccountDetailUiState(),
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

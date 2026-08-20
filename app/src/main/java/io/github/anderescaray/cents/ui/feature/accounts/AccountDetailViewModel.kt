package io.github.anderescaray.cents.ui.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.ui.navigation.Destination
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
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** One day of movements inside a single account. */
data class AccountDay(
    val date: LocalDate,
    val items: List<TransactionDetails>,
)

data class AccountDetailUiState(
    val account: AccountWithBalance? = null,
    val monthSummary: PeriodSummary? = null,
    val days: List<AccountDay> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
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
        transactionRepository: TransactionRepository,
        savedStateHandle: SavedStateHandle,
        clock: Clock,
    ) : ViewModel() {
        private val accountId: Long = savedStateHandle.toRoute<Destination.AccountDetail>().accountId

        private val account: Flow<AccountWithBalance?> =
            accountRepository.observeAccountWithBalance(accountId)

        /**
         * The month's totals, re-queried only when the currency they are expressed in
         * changes — not on every movement, which is what watching the whole account would
         * do, since its balance moves with each one.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val monthSummary: Flow<PeriodSummary?> =
            account
                .map { it?.account?.currency }
                .distinctUntilChanged()
                .flatMapLatest { currency ->
                    if (currency == null) {
                        flowOf(null)
                    } else {
                        val today = LocalDate.now(clock)
                        transactionRepository.observeAccountSummary(
                            accountId = accountId,
                            from = today.withDayOfMonth(1),
                            to = today.withDayOfMonth(today.lengthOfMonth()),
                            currency = currency,
                        )
                    }
                }

        val uiState: StateFlow<AccountDetailUiState> =
            combine(
                account,
                transactionRepository.observeAccountTransactionDetails(accountId),
                monthSummary,
            ) { account, movements, summary ->
                AccountDetailUiState(
                    account = account,
                    monthSummary = summary,
                    days =
                        movements
                            .groupBy { it.transaction.date }
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

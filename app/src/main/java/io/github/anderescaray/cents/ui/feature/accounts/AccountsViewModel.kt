package io.github.anderescaray.cents.ui.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AccountsUiState(
    val active: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
    val isLoading: Boolean = true,
    /**
     * The last removal asked for from this screen did not happen. The foreign key can
     * refuse a delete that was legitimate when it was offered — a movement landing in the
     * account between counting it and confirming — and a swipe that appears to do nothing
     * is worse than one that says why.
     */
    val removalFailed: Boolean = false,
) {
    val showEmptyState: Boolean get() = !isLoading && active.isEmpty()

    /**
     * The sum of every active account, and null when they do not share a currency.
     *
     * Adding euros to dollars needs a conversion rate the app does not have, and a total
     * that quietly pretends otherwise is exactly the kind of number this app must not
     * show (PRD §3, "totals across currencies are approximate" — so until there is a
     * conversion step, there is no total).
     */
    val total: Money?
        get() {
            val currencies = active.map { it.account.currency }.distinct()
            val currency = currencies.singleOrNull() ?: return null
            return active.fold(Money.zero(currency)) { sum, item -> sum + item.balance }
        }
}

/** The Accounts tab: what the owner has, and what is in it. */
@HiltViewModel
class AccountsViewModel
    @Inject
    constructor(
        accountRepository: AccountRepository,
    ) : ViewModel() {
        private val failed = MutableStateFlow(false)

        /**
         * The same delete question the editor asks, reachable here by swiping a row.
         *
         * Swiping is a shortcut to the question, never past it: the sheet that names how
         * many movements are at stake is the same object in both places (`DECISIONS.md` D7).
         */
        val removal =
            AccountRemoval(accountRepository, viewModelScope) { outcome ->
                failed.value = outcome.isFailure
            }

        val uiState: StateFlow<AccountsUiState> =
            combine(
                accountRepository.observeAccountsWithBalances(includeArchived = true),
                failed,
            ) { accounts, removalFailed ->
                AccountsUiState(
                    active = accounts.filterNot { it.account.archived },
                    archived = accounts.filter { it.account.archived },
                    isLoading = false,
                    removalFailed = removalFailed,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AccountsUiState(),
            )

        /** Asks about [account], clearing whatever the previous attempt had to say. */
        fun requestRemoval(account: Account) {
            failed.value = false
            removal.request(account)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

package io.github.anderescaray.cents.ui.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.MoneyFiguresRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.usecase.netWorthOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AccountsUiState(
    val active: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
    /**
     * Cash and bank, invested, and the two together — and null when they cannot honestly be added
     * (rule 2). The tab then falls back to [total], which is still true.
     */
    val netWorth: NetWorth? = null,
    /** Which of those figures the owner chose to see (`DECISIONS.md` D34). */
    val figures: MoneyFigures = MoneyFigures(),
    val isLoading: Boolean = true,
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
        portfolioRepository: PortfolioRepository,
        figuresRepository: MoneyFiguresRepository,
    ) : ViewModel() {
        /**
         * The same delete question the editor asks, reachable here by swiping a row.
         *
         * Swiping is a shortcut to the question, never past it: the sheet that names how
         * many movements are at stake is the same object in both places (`DECISIONS.md` D7).
         */
        val removal = AccountRemoval(accountRepository, viewModelScope)

        /**
         * The accounts and the portfolio together, because the tab now answers a question that
         * needs both.
         *
         * The portfolio is read here rather than the net worth being computed in the Investments
         * tab and passed across: this is the screen that asks *what do I have*, and the two tabs
         * would otherwise each hold half of one sum.
         */
        val uiState: StateFlow<AccountsUiState> =
            combine(
                accountRepository.observeAccountsWithBalances(includeArchived = true),
                portfolioRepository.observePortfolio(),
                figuresRepository.observe(),
            ) { accounts, portfolio, figures ->
                val active = accounts.filterNot { it.account.archived }
                val archived = accounts.filter { it.account.archived }
                AccountsUiState(
                    active = active,
                    archived = archived,
                    netWorth = netWorthOf(accounts, portfolio),
                    figures = figures,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AccountsUiState(),
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

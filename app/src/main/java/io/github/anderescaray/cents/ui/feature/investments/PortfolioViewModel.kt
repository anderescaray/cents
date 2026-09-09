package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.PortfolioTotal
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One heading of the portfolio: a kind of asset, and what is held of it. */
@Immutable
data class PortfolioGroup(
    val assetClass: AssetClass,
    val positions: List<Position>,
)

@Immutable
data class PortfolioUiState(
    val loading: Boolean = true,
    val groups: List<PortfolioGroup> = emptyList(),
    val totals: List<PortfolioTotal> = emptyList(),
    /** Nothing can be held until there is an account to hold it in, and the empty state says so. */
    val hasAccount: Boolean = false,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

/**
 * The portfolio: what is held, grouped by what kind of thing it is.
 *
 * The grouping is the owner's own request — stocks, ETFs, index funds and crypto each under
 * their own heading — and it is also the split that stops being cosmetic the day prices arrive
 * from a provider: a ticker can be looked up, an ISIN mostly cannot (PRD 5.1).
 *
 * **A fully sold holding is not listed.** It has no quantity, so it has nothing to be worth;
 * what it made is on its own screen, reachable while any of it is still held. That is a choice
 * this screen makes rather than a fact of the data, and it is the reason `Position.isOpen`
 * exists.
 */
@HiltViewModel
class PortfolioViewModel
    @Inject
    constructor(
        portfolioRepository: PortfolioRepository,
        accountRepository: AccountRepository,
    ) : ViewModel() {
        val uiState: StateFlow<PortfolioUiState> =
            combine(
                portfolioRepository.observePortfolio(),
                accountRepository.observeAccounts(includeArchived = true),
            ) { portfolio, accounts ->
                PortfolioUiState(
                    loading = false,
                    groups =
                        portfolio.openPositionsByClass().map { (assetClass, positions) ->
                            PortfolioGroup(assetClass, positions)
                        },
                    totals = portfolio.totals(),
                    hasAccount = accounts.isNotEmpty(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = PortfolioUiState(),
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

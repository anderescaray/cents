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
    /** Fully sold holdings. Absent from [groups] and from [totals]. */
    val closedPositions: List<Position> = emptyList(),
    /**
     * Nothing can be held until there is an account to hold it in, and the empty state says so.
     *
     * Archived ones count, because the holding form offers them: a portfolio can legitimately
     * live in an account that has been put away, and answering "no accounts" here while the
     * editor would have offered one is how the "+" button disappears in front of somebody who
     * has an account.
     */
    val hasAccount: Boolean = false,
) {
    /** Nothing open and nothing closed. A sold-out portfolio is not this: its result is still there. */
    val isEmpty: Boolean get() = groups.isEmpty() && closedPositions.isEmpty()
}

/**
 * The portfolio: what is held, grouped by what kind of thing it is.
 *
 * The grouping is the owner's own request — stocks, ETFs, index funds and crypto each under
 * their own heading — and it is also the split that stops being cosmetic the day prices arrive
 * from a provider: a ticker can be looked up, an ISIN mostly cannot (PRD 5.1).
 *
 * **A fully sold holding is not part of the portfolio.** It has no quantity, so it has nothing
 * to be worth, and it stays out of [PortfolioUiState.totals]. What it made is listed underneath,
 * collapsed, because that profit is recorded and was otherwise unreachable. A holding that was
 * never bought is not closed: a quantity of zero is also what you have before the first trade.
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
                    closedPositions = portfolio.closedPositions,
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

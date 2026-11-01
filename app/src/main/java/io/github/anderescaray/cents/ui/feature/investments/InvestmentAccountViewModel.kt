package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.ChartPoint
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.InvestmentAccount
import io.github.anderescaray.cents.domain.repository.ChartScaleRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.usecase.chartPoints
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class InvestmentAccountUiState(
    val loading: Boolean = true,
    /** Null once loaded means the account is gone, or is not a broker. */
    val account: InvestmentAccount? = null,
    val scale: ChartScale = ChartScale.WEEK,
    val points: List<ChartPoint> = emptyList(),
)

/** One broker account's investments (D40). */
@HiltViewModel
class InvestmentAccountViewModel
    @Inject
    constructor(
        portfolio: PortfolioRepository,
        private val chartScales: ChartScaleRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val accountId = savedStateHandle.toRoute<Destination.InvestmentAccount>().accountId
        private val chartKey = ChartScaleRepository.accountChart(accountId)

        val uiState: StateFlow<InvestmentAccountUiState> =
            combine(portfolio.observeAccount(accountId), chartScales.observe(chartKey)) { account, scale ->
                InvestmentAccountUiState(
                    loading = false,
                    account = account,
                    scale = scale,
                    points =
                        account
                            ?.let {
                                chartPoints(
                                    it.firstDate,
                                    LocalDate.now(clock),
                                    scale,
                                    it::totalOn,
                                )
                            }.orEmpty(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InvestmentAccountUiState())

        fun onScaleChange(scale: ChartScale) {
            viewModelScope.launch { chartScales.set(chartKey, scale) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

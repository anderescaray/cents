package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.ChartPoint
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.InvestmentOverview
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.repository.ChartScaleRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.usecase.chartPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class InvestmentsUiState(
    val loading: Boolean = true,
    val overview: InvestmentOverview = InvestmentOverview(currency = null, accounts = emptyList()),
    val scale: ChartScale = ChartScale.WEEK,
    val points: List<ChartPoint> = emptyList(),
) {
    val hasAccounts: Boolean get() = overview.accounts.isNotEmpty()
}

/** Invested: every broker account, what they hold together, and how it has moved (D40). */
@HiltViewModel
class InvestmentsViewModel
    @Inject
    constructor(
        portfolio: PortfolioRepository,
        private val chartScales: ChartScaleRepository,
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<InvestmentsUiState> =
            combine(
                portfolio.observeOverview(),
                chartScales.observe(ChartScaleRepository.TOTAL_CHART),
            ) { overview, scale ->
                val currency = overview.currency
                InvestmentsUiState(
                    loading = false,
                    overview = overview,
                    scale = scale,
                    points =
                        if (currency == null) {
                            emptyList()
                        } else {
                            chartPoints(overview.firstDate, LocalDate.now(clock), scale) { day ->
                                overview.totalOn(day) ?: Money.zero(currency)
                            }
                        },
                )
            }.flowOn(Dispatchers.Default) // Folds and chart points: not on the main thread (rule 4).
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InvestmentsUiState())

        fun onScaleChange(scale: ChartScale) {
            viewModelScope.launch { chartScales.set(ChartScaleRepository.TOTAL_CHART, scale) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

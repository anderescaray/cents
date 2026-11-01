package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.ChartPoint
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.ChartScaleRepository
import io.github.anderescaray.cents.domain.repository.HoldingValueRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.usecase.chartPoints
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class AssetUiState(
    val loading: Boolean = true,
    /** Null once loaded means the asset was deleted. */
    val position: Position? = null,
    val accountName: String = "",
    val scale: ChartScale = ChartScale.WEEK,
    val points: List<ChartPoint> = emptyList(),
    /** A value the owner asked to delete, waiting for the confirmation. */
    val deletingValue: HoldingValue? = null,
    /** The last deletion could not be written; nothing was changed. */
    val deleteFailed: Boolean = false,
)

/** One asset (D40). */
@HiltViewModel
class AssetViewModel
    @Inject
    constructor(
        portfolio: PortfolioRepository,
        accounts: AccountRepository,
        private val values: HoldingValueRepository,
        private val chartScales: ChartScaleRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val holdingId = savedStateHandle.toRoute<Destination.HoldingDetail>().holdingId
        private val chartKey = ChartScaleRepository.holdingChart(holdingId)
        private val deleting = MutableStateFlow<HoldingValue?>(null)
        private val deleteFailed = MutableStateFlow(false)

        val uiState: StateFlow<AssetUiState> =
            combine(
                portfolio.observePosition(holdingId),
                accounts.observeAccounts(includeArchived = true),
                chartScales.observe(chartKey),
                combine(deleting, deleteFailed, ::Pair),
            ) { position, allAccounts, scale, (pending, failed) ->
                AssetUiState(
                    loading = false,
                    position = position,
                    accountName = allAccounts.firstOrNull { it.id == position?.holding?.accountId }?.name.orEmpty(),
                    scale = scale,
                    points =
                        position
                            ?.let {
                                chartPoints(
                                    it.firstDate,
                                    LocalDate.now(clock),
                                    scale,
                                    it::valueOn,
                                )
                            }.orEmpty(),
                    deletingValue = pending,
                    deleteFailed = failed,
                )
            }.flowOn(Dispatchers.Default) // Folds and chart points: not on the main thread (rule 4).
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AssetUiState())

        fun onScaleChange(scale: ChartScale) {
            viewModelScope.launch { chartScales.set(chartKey, scale) }
        }

        fun requestValueDeletion(value: HoldingValue) {
            deleting.value = value
        }

        fun cancelValueDeletion() {
            deleting.value = null
        }

        fun confirmValueDeletion() {
            val value = deleting.value ?: return
            deleting.update { null }
            viewModelScope.launch {
                // A failed write says so, as the forms do, rather than taking the app down.
                deleteFailed.value = runCatching { values.deleteValue(value.id) }.isFailure
            }
        }

        fun dismissDeleteFailure() {
            deleteFailed.value = false
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

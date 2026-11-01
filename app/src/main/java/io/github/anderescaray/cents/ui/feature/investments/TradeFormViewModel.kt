package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.repository.TradeRepository
import io.github.anderescaray.cents.domain.usecase.positionOf
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.AmountEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class TradeFormUiState(
    val isNew: Boolean = true,
    val side: TradeSide = TradeSide.BUY,
    val amountInput: String = AmountEntry.EMPTY,
    val date: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val sellsAll: Boolean = false,
    val note: String = "",
    val currency: String? = null,
    /** What the asset is worth and cost now, for a new sale's result before it is saved. */
    val position: Position? = null,
    val pickingDate: Boolean = false,
    val deleting: Boolean = false,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    val amountMinor: Long get() = AmountEntry.toMinorUnits(amountInput)

    val canSave: Boolean get() = amountMinor > 0 && currency != null && !saving

    /**
     * What a new sale would make, worked out by `positionOf` itself with the sale added at its
     * date, so the preview is the result that will be written. Null for a purchase, an edit, or
     * nothing typed yet.
     */
    val resultPreview: Money?
        get() {
            val held = position ?: return null
            if (!isNew || side != TradeSide.SELL || amountMinor <= 0) return null
            val sale =
                Trade(
                    id = Long.MAX_VALUE,
                    holdingId = held.holding.id,
                    side = TradeSide.SELL,
                    amount = Money(amountMinor, held.holding.currency),
                    date = date,
                    note = null,
                    sellsAll = sellsAll,
                )
            return positionOf(
                held.holding,
                held.trades + sale,
                held.values,
            ).sales.firstOrNull { it.trade.id == sale.id }?.result
        }
}

/** A purchase or a sale, as an amount and a date (D40). */
@HiltViewModel
class TradeFormViewModel
    @Inject
    constructor(
        private val tradeRepository: TradeRepository,
        private val portfolio: PortfolioRepository,
        clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<Destination.TradeForm>()
        private val tradeId: Long? = route.tradeId.takeIf { it != Destination.TradeForm.NEW }
        private val today = LocalDate.now(clock)

        private val form =
            MutableStateFlow(
                TradeFormUiState(
                    isNew = tradeId == null,
                    side = if (route.selling) TradeSide.SELL else TradeSide.BUY,
                    date = today,
                    today = today,
                ),
            )

        val uiState: StateFlow<TradeFormUiState> = form.asStateFlow()

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val position = portfolio.observePosition(route.holdingId).first()
            val trade = tradeId?.let { tradeRepository.findById(it) }
            form.update { state ->
                state.copy(
                    currency = position?.holding?.currency,
                    position = position,
                    side = trade?.side ?: state.side,
                    amountInput = trade?.let { AmountEntry.fromMinorUnits(it.amount.minorUnits) } ?: state.amountInput,
                    date = trade?.date ?: state.date,
                    sellsAll = trade?.sellsAll ?: state.sellsAll,
                    note = trade?.note.orEmpty(),
                )
            }
        }

        fun onAmountTyped(value: String) = form.update { it.copy(amountInput = AmountEntry.sanitize(value)) }

        fun onSellsAllChanged(value: Boolean) = form.update { it.copy(sellsAll = value) }

        fun onNoteChanged(value: String) = form.update { it.copy(note = value) }

        fun openDatePicker() = form.update { it.copy(pickingDate = true) }

        fun dismissDatePicker() = form.update { it.copy(pickingDate = false) }

        fun onDateSelected(value: LocalDate) = form.update { it.copy(date = value, pickingDate = false) }

        fun dismissWriteFailure() = form.update { it.copy(writeFailed = false) }

        fun requestDeletion() = form.update { it.copy(deleting = true) }

        fun cancelDeletion() = form.update { it.copy(deleting = false) }

        fun confirmDeletion() {
            val id = tradeId ?: return
            form.update { it.copy(deleting = false, saving = true) }
            viewModelScope.launch {
                val done = runCatching { tradeRepository.deleteTrade(id) }
                form.update { it.copy(saving = false, writeFailed = done.isFailure, finished = done.isSuccess) }
            }
        }

        fun save() {
            val state = form.value
            val currency = state.currency ?: return
            if (!state.canSave) return
            form.update { it.copy(saving = true, writeFailed = false) }
            val trade =
                Trade(
                    id = tradeId ?: 0,
                    holdingId = route.holdingId,
                    side = state.side,
                    amount = Money(state.amountMinor, currency),
                    date = state.date,
                    note = state.note.trim().ifBlank { null },
                    sellsAll = state.side == TradeSide.SELL && state.sellsAll,
                )
            viewModelScope.launch {
                val written =
                    runCatching {
                        if (tradeId == null) tradeRepository.addTrade(trade) else tradeRepository.updateTrade(trade)
                    }
                form.update { it.copy(saving = false, writeFailed = written.isFailure, finished = written.isSuccess) }
            }
        }
    }

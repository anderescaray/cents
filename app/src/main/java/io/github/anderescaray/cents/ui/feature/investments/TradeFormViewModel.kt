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
    /**
     * What a new sale would make, worked out by `positionOf` itself with the sale added at its
     * date, so the preview is the result that will be written. Null for a purchase, an edit, or
     * nothing typed yet. Worked out in the view model, not by the screen (`CLAUDE.md` §5).
     */
    val resultPreview: Money? = null,
    /**
     * The worth a part sale of at least this much would empty the asset against: its last value,
     * carried to the sale's date. Null when the sale is below it, sells everything, or is a
     * purchase. Saving waits until the owner says it sells everything or types a newer value,
     * because a part sale read as a whole one would drop what is still held.
     */
    val emptiesAgainst: Money? = null,
) {
    val amountMinor: Long get() = AmountEntry.toMinorUnits(amountInput)

    /** A sale of everything may return nothing: an asset that went to zero still has to close. */
    private val amountAccepted: Boolean
        get() = amountMinor > 0 || (side == TradeSide.SELL && sellsAll)

    val canSave: Boolean get() = amountAccepted && currency != null && !saving && emptiesAgainst == null
}

/**
 * [state] with what follows from it worked out again: the sale's result and whether, as a part
 * sale, it would empty the asset.
 */
internal fun derived(
    state: TradeFormUiState,
    editedTradeId: Long?,
): TradeFormUiState {
    val held = state.position
    val amount = state.amountMinor
    val nothingToSell = amount <= 0 && !state.sellsAll
    if (held == null || state.side != TradeSide.SELL || nothingToSell) {
        return state.copy(resultPreview = null, emptiesAgainst = null)
    }
    val others = held.trades.filter { it.id != editedTradeId }
    val before = positionOf(held.holding, others, held.values).valueOn(state.date)
    val sale =
        Trade(
            id = editedTradeId ?: Long.MAX_VALUE,
            holdingId = held.holding.id,
            side = TradeSide.SELL,
            amount = Money(amount, held.holding.currency),
            date = state.date,
            note = null,
            sellsAll = state.sellsAll,
        )
    val preview =
        if (state.isNew) {
            positionOf(held.holding, others + sale, held.values).sales.firstOrNull { it.trade.id == sale.id }?.result
        } else {
            null
        }
    val empties = !state.sellsAll && amount > 0 && before.minorUnits > 0 && amount >= before.minorUnits
    return state.copy(resultPreview = preview, emptiesAgainst = before.takeIf { empties })
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

        /** Every change goes through here, so what follows from the form is never stale. */
        private fun edit(change: (TradeFormUiState) -> TradeFormUiState) = form.update { derived(change(it), tradeId) }

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val position = portfolio.observePosition(route.holdingId).first()
            val trade = tradeId?.let { tradeRepository.findById(it) }
            edit { state ->
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

        fun onAmountTyped(value: String) = edit { it.copy(amountInput = AmountEntry.sanitize(value)) }

        fun onSellsAllChanged(value: Boolean) = edit { it.copy(sellsAll = value) }

        fun onNoteChanged(value: String) = form.update { it.copy(note = value) }

        fun openDatePicker() = form.update { it.copy(pickingDate = true) }

        fun dismissDatePicker() = form.update { it.copy(pickingDate = false) }

        fun onDateSelected(value: LocalDate) = edit { it.copy(date = value, pickingDate = false) }

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

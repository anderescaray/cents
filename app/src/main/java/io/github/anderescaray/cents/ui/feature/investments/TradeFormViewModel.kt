package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Quantity
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.repository.HoldingRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.repository.TradeRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.QuantityEntry
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
    val quantityInput: String = QuantityEntry.EMPTY,
    val priceInput: String = AmountEntry.EMPTY,
    val feeInput: String = AmountEntry.EMPTY,
    val date: LocalDate = LocalDate.EPOCH,
    val today: LocalDate = LocalDate.EPOCH,
    val currency: String = "EUR",
    /** What is held *without* this trade, so a sale can be checked against it. */
    val heldElsewhere: Quantity = Quantity.ZERO,
    val pickingDate: Boolean = false,
    val deleting: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val quantity: Quantity get() = QuantityEntry.toQuantity(quantityInput)

    val pricePerUnit: Money get() = Money(AmountEntry.toMinorUnits(priceInput), currency)

    val fee: Money get() = Money(AmountEntry.toMinorUnits(feeInput), currency)

    /** What the trade moves, the fee counted against you in both directions. */
    val total: Money
        get() {
            val gross = quantity * pricePerUnit
            return if (side == TradeSide.BUY) gross + fee else gross - fee
        }

    /**
     * A sale of more than is held. It is refused rather than clamped, because the owner meant
     * one of two things — a wrong quantity, or a missing purchase — and silently selling only
     * what is there would record neither.
     */
    val sellsMoreThanHeld: Boolean
        get() = side == TradeSide.SELL && quantity > heldElsewhere

    val canSave: Boolean
        get() = !quantity.isZero && priceInput.isNotEmpty() && !sellsMoreThanHeld && !saving
}

/**
 * Recording a purchase or a sale.
 *
 * The quantity is typed with up to eight decimals ([QuantityEntry]) and the price in euros with
 * the comma asking for cents ([AmountEntry]) — the same rules the movement keypad follows, so
 * no two number fields in this app disagree about what a half-typed number means.
 *
 * What is held is read *without this trade* while it is being edited, which is what lets the
 * sale check be right when an existing sale is made larger.
 */
@HiltViewModel
class TradeFormViewModel
    @Inject
    constructor(
        private val tradeRepository: TradeRepository,
        private val holdingRepository: HoldingRepository,
        private val portfolioRepository: PortfolioRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<Destination.TradeForm>()
        private val holdingId: Long = route.holdingId
        private val tradeId: Long? = route.tradeId.takeIf { it != Destination.TradeForm.NEW }

        private val form =
            MutableStateFlow(
                TradeFormUiState(
                    isNew = tradeId == null,
                    date = LocalDate.now(clock),
                    today = LocalDate.now(clock),
                ),
            )

        val uiState: StateFlow<TradeFormUiState> = form.asStateFlow()

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val holding = holdingRepository.findById(holdingId)
            val trade = tradeId?.let { tradeRepository.findById(it) }
            val held = portfolioRepository.observePosition(holdingId).first()?.quantity ?: Quantity.ZERO

            form.update { state ->
                state
                    .withFieldsOf(trade)
                    .copy(
                        currency = holding?.currency ?: state.currency,
                        heldElsewhere = heldWithout(trade, held),
                    )
            }
        }

        /**
         * What is held *without* [trade] in it.
         *
         * The position already counts the trade being edited, so its own quantity comes back
         * out: what decides whether a sale can happen is what is held besides it. Without this,
         * enlarging an existing sale would be measured against a position that sale has already
         * shrunk, and the form would refuse a correction that is perfectly legitimate.
         */
        private fun heldWithout(
            trade: Trade?,
            held: Quantity,
        ): Quantity =
            when {
                trade == null -> held
                trade.side == TradeSide.BUY -> held - trade.quantity
                else -> held + trade.quantity
            }

        /** The form filled in from an existing trade, or left as it is when creating one. */
        private fun TradeFormUiState.withFieldsOf(trade: Trade?): TradeFormUiState =
            if (trade == null) {
                this
            } else {
                copy(
                    side = trade.side,
                    quantityInput = QuantityEntry.fromQuantity(trade.quantity),
                    priceInput = AmountEntry.fromMinorUnits(trade.pricePerUnit.minorUnits),
                    feeInput = AmountEntry.fromMinorUnits(trade.fee.minorUnits),
                    date = trade.date,
                )
            }

        fun onSideChanged(side: TradeSide) {
            form.update { it.copy(side = side) }
        }

        fun onQuantityTyped(typed: String) {
            form.update { it.copy(quantityInput = QuantityEntry.sanitize(typed)) }
        }

        fun onPriceTyped(typed: String) {
            form.update { it.copy(priceInput = AmountEntry.sanitize(typed)) }
        }

        fun onFeeTyped(typed: String) {
            form.update { it.copy(feeInput = AmountEntry.sanitize(typed)) }
        }

        fun openDatePicker() {
            form.update { it.copy(pickingDate = true) }
        }

        fun onDateSelected(date: LocalDate) {
            form.update { it.copy(date = date, pickingDate = false) }
        }

        fun dismissDatePicker() {
            form.update { it.copy(pickingDate = false) }
        }

        fun requestDeletion() {
            form.update { it.copy(deleting = true) }
        }

        fun cancelDeletion() {
            form.update { it.copy(deleting = false) }
        }

        fun confirmDeletion() {
            val id = tradeId ?: return
            viewModelScope.launch {
                tradeRepository.deleteTrade(id)
                form.update { it.copy(deleting = false, finished = true) }
            }
        }

        fun save() {
            val state = form.value
            if (!state.canSave) return
            form.update { it.copy(saving = true) }
            viewModelScope.launch {
                val trade =
                    Trade(
                        id = tradeId ?: 0L,
                        holdingId = holdingId,
                        side = state.side,
                        quantity = state.quantity,
                        pricePerUnit = state.pricePerUnit,
                        fee = state.fee,
                        date = state.date,
                        note = null,
                    )
                if (tradeId == null) {
                    tradeRepository.addTrade(trade)
                } else {
                    tradeRepository.updateTrade(trade)
                }
                form.update { it.copy(saving = false, finished = true) }
            }
        }
    }

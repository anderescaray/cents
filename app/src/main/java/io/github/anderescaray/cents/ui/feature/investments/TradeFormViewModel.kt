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

/** Which way round the owner is describing the size of a trade. */
enum class QuantityBasis {
    /** Units, which is what the app stores and what a later sale is measured in. */
    UNITS,

    /**
     * The euros that went in, which is how the trade was actually made.
     *
     * A platform like Trade Republic sells you 500 € of something and works out the units
     * afterwards — so this is the number the owner has in front of them, and turning it into
     * units by hand before they can type it is arithmetic homework the app can do.
     */
    AMOUNT,
}

@Immutable
data class TradeFormUiState(
    val isNew: Boolean = true,
    val side: TradeSide = TradeSide.BUY,
    val quantityBasis: QuantityBasis = QuantityBasis.UNITS,
    /** What was spent, when the size is being given in euros. */
    val amountInput: String = AmountEntry.EMPTY,
    val quantityInput: String = QuantityEntry.EMPTY,
    val priceInput: String = AmountEntry.EMPTY,
    val feeInput: String = AmountEntry.EMPTY,
    val date: LocalDate = LocalDate.EPOCH,
    val today: LocalDate = LocalDate.EPOCH,
    val currency: String = "EUR",
    /** Free text, kept as typed and trimmed only when saved. Blank means no note. */
    val note: String = "",
    /** What is held *without* this trade, so a sale can be checked against it. */
    val heldElsewhere: Quantity = Quantity.ZERO,
    val pickingDate: Boolean = false,
    val deleting: Boolean = false,
    val saving: Boolean = false,
    /** The database refused the write. It is shown, and nothing is retried automatically. */
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    /**
     * How much of the instrument this trade moves.
     *
     * Derived when the owner is typing euros, so the stored trade is in units either way: units
     * are what a partial sale reduces, what an average cost divides by, and what a price
     * multiplies. Storing euros would mean deriving units on every read instead of once here.
     */
    val quantity: Quantity
        get() =
            when (quantityBasis) {
                QuantityBasis.UNITS -> QuantityEntry.toQuantity(quantityInput)
                QuantityBasis.AMOUNT -> Quantity.forAmount(amountSpent, pricePerUnit)
            }

    /** What the owner typed as spent, before any fee. */
    val amountSpent: Money get() = Money(AmountEntry.toMinorUnits(amountInput), currency)

    /**
     * Whether the size is being given in euros, which a correction never is: a correction says
     * how many units are held and moves no money at all.
     */
    val usesAmount: Boolean get() = quantityBasis == QuantityBasis.AMOUNT && !isCorrection

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

    val isCorrection: Boolean get() = side == TradeSide.CORRECTION

    /**
     * A correction needs no price and no fee: it moves units, not money. It may also be zero —
     * "I actually hold nothing" is a legitimate thing to say about a position the app believes
     * in, and it is the only entry in this form allowed to be.
     */
    val canSave: Boolean
        get() =
            when {
                saving -> false

                isCorrection -> quantityInput.isNotEmpty()

                // Typing euros needs a price to divide by before it is a quantity at all, so
                // both fields have to be there — and what they come to still has to be more
                // than nothing.
                usesAmount -> !quantity.isZero && amountInput.isNotEmpty() && priceInput.isNotEmpty()

                else -> !quantity.isZero && priceInput.isNotEmpty() && !sellsMoreThanHeld
            }
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
        private val opensAsCorrection: Boolean = route.correcting && tradeId == null

        private val form =
            MutableStateFlow(
                TradeFormUiState(
                    isNew = tradeId == null,
                    side = if (opensAsCorrection) TradeSide.CORRECTION else TradeSide.BUY,
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
                val loaded =
                    state
                        .withFieldsOf(trade)
                        .copy(
                            currency = holding?.currency ?: state.currency,
                            heldElsewhere = heldWithout(trade, held),
                        )
                // A correction opens on what the app believes is held, and that is only knowable
                // once the position has been read — so it is filled in here rather than at
                // construction, where the answer would have been zero.
                //
                // The condition is "the form is on a correction and has no quantity yet", not
                // "the form opened as one": switching to Correct before this finished loading
                // asked `heldElsewhere` a question it could not answer, and left the field empty
                // in exactly the case it exists to fill.
                if (loaded.isCorrection && loaded.quantityInput.isEmpty()) {
                    loaded.copy(quantityInput = QuantityEntry.fromQuantity(loaded.heldElsewhere))
                } else {
                    loaded
                }
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
                    note = trade.note.orEmpty(),
                )
            }

        fun onSideChanged(side: TradeSide) {
            form.update { state ->
                // Coming into a correction, the field starts on what the app currently believes
                // is held — the number the owner is about to disagree with. Typing over it is
                // the whole gesture, and starting from empty would make them look it up first.
                if (side == TradeSide.CORRECTION && !state.isCorrection) {
                    // A correction is in units by definition: it says what is held, and holding
                    // is counted in units rather than in euros.
                    state.copy(
                        side = side,
                        quantityBasis = QuantityBasis.UNITS,
                        quantityInput = QuantityEntry.fromQuantity(state.heldElsewhere),
                    )
                } else {
                    state.copy(side = side)
                }
            }
        }

        fun onQuantityTyped(typed: String) {
            form.update { it.copy(quantityInput = QuantityEntry.sanitize(typed)) }
        }

        fun onAmountTyped(typed: String) {
            form.update { it.copy(amountInput = AmountEntry.sanitize(typed)) }
        }

        /**
         * Switches between describing the trade in units and in euros, **converting what is
         * already typed** rather than clearing it.
         *
         * The same bargain the price editor strikes between *per unit* and *total value*: a
         * control that empties the fields is a control nobody touches twice.
         */
        fun onQuantityBasisChanged(basis: QuantityBasis) {
            form.update { state ->
                if (basis == state.quantityBasis) {
                    state
                } else {
                    when (basis) {
                        QuantityBasis.AMOUNT -> {
                            state.copy(
                                quantityBasis = basis,
                                amountInput =
                                    if (state.quantity.isZero) {
                                        state.amountInput
                                    } else {
                                        AmountEntry.fromMinorUnits((state.quantity * state.pricePerUnit).minorUnits)
                                    },
                            )
                        }

                        QuantityBasis.UNITS -> {
                            state.copy(
                                quantityBasis = basis,
                                quantityInput =
                                    if (state.quantity.isZero) {
                                        state.quantityInput
                                    } else {
                                        QuantityEntry.fromQuantity(state.quantity)
                                    },
                            )
                        }
                    }
                }
            }
        }

        fun onPriceTyped(typed: String) {
            form.update { it.copy(priceInput = AmountEntry.sanitize(typed)) }
        }

        fun onFeeTyped(typed: String) {
            form.update { it.copy(feeInput = AmountEntry.sanitize(typed)) }
        }

        fun onNoteChanged(note: String) {
            form.update { it.copy(note = note) }
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
                val removed = runCatching { tradeRepository.deleteTrade(id) }
                form.update {
                    it.copy(deleting = false, writeFailed = removed.isFailure, finished = removed.isSuccess)
                }
            }
        }

        fun dismissWriteFailure() {
            form.update { it.copy(writeFailed = false) }
        }

        fun save() {
            val state = form.value
            if (!state.canSave) return
            form.update { it.copy(saving = true) }
            viewModelScope.launch {
                val zero = Money.zero(state.currency)
                val trade =
                    Trade(
                        id = tradeId ?: 0L,
                        holdingId = holdingId,
                        side = state.side,
                        quantity = state.quantity,
                        // A correction stores no money at all, rather than whatever happened to
                        // be left in the fields before the segment was switched.
                        pricePerUnit = if (state.isCorrection) zero else state.pricePerUnit,
                        fee = if (state.isCorrection) zero else state.fee,
                        date = state.date,
                        // Carried through on every save, a correction's included: a form that
                        // did not offer the note used to write null here, which erased the
                        // note of any trade it edited.
                        note = state.note.trim().ifBlank { null },
                    )
                // The holding can be deleted from another screen while this form is open, and
                // `trades.holdingId` is RESTRICT — so the write can be refused. An exception
                // escaping a view model's scope takes the app with it; this becomes an alert,
                // the same bargain `AccountRemoval` strikes.
                val written =
                    runCatching {
                        if (tradeId == null) {
                            tradeRepository.addTrade(trade)
                        } else {
                            tradeRepository.updateTrade(trade)
                        }
                    }
                form.update {
                    it.copy(saving = false, writeFailed = written.isFailure, finished = written.isSuccess)
                }
            }
        }
    }

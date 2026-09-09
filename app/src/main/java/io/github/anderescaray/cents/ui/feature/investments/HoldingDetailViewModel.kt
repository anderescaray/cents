package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.Quantity
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.divideRoundingHalfUp
import io.github.anderescaray.cents.domain.repository.HoldingRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.repository.PriceQuoteRepository
import io.github.anderescaray.cents.domain.repository.TradeRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.AmountEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@Immutable
data class HoldingDetailUiState(
    val loading: Boolean = true,
    val position: Position? = null,
    val trades: List<Trade> = emptyList(),
) {
    /**
     * The holding is gone: deleted here, from this screen. There is no separate flag for it,
     * because a deleted holding stops being observable and that *is* the signal — a flag beside
     * it would be a second truth that could disagree.
     */
    val gone: Boolean get() = !loading && position == null
}

/**
 * Which number the owner is typing when they price a holding.
 *
 * Both say the same thing and the app stores the same thing either way — one price per unit. The
 * choice exists because the *question* differs: a broker's app shows you both, and being made to
 * divide one by the other before you can type it is how a correction becomes arithmetic homework.
 */
enum class PriceBasis {
    /** What one unit is worth. */
    PER_UNIT,

    /** What the whole position is worth. Divided by the quantity to get the price. */
    TOTAL,
}

/** The price being typed, which is form state rather than something the database knows yet. */
@Immutable
data class PriceEntryState(
    val editing: Boolean = false,
    val basis: PriceBasis = PriceBasis.PER_UNIT,
    val input: String = AmountEntry.EMPTY,
    val date: LocalDate = LocalDate.EPOCH,
    val pickingDate: Boolean = false,
)

/**
 * One holding: what is held, what it cost, what it is worth, and every trade behind it.
 *
 * **The price is typed here and stored against the symbol**, not against the holding, so the
 * same instrument in two accounts cannot be worth two different things. It carries the date it
 * was true rather than the date it was entered — a price read off a broker's app on Friday and
 * typed on Sunday is Friday's price, and the screen says so rather than implying it is fresh.
 */
@HiltViewModel
class HoldingDetailViewModel
    @Inject
    constructor(
        portfolioRepository: PortfolioRepository,
        private val tradeRepository: TradeRepository,
        private val priceQuoteRepository: PriceQuoteRepository,
        private val holdingRepository: HoldingRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val holdingId: Long = savedStateHandle.toRoute<Destination.HoldingDetail>().holdingId

        private val removal = MutableStateFlow(false)
        private val priceForm = MutableStateFlow(PriceEntryState())

        val price: StateFlow<PriceEntryState> = priceForm.asStateFlow()

        /** Set when the delete confirmation is up; it carries how many trades would go. */
        val pendingDeletion: StateFlow<Boolean> = removal.asStateFlow()

        val uiState: StateFlow<HoldingDetailUiState> =
            combine(
                portfolioRepository.observePosition(holdingId),
                tradeRepository.observeTrades(holdingId),
            ) { position, trades ->
                HoldingDetailUiState(loading = false, position = position, trades = trades)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = HoldingDetailUiState(),
            )

        /** Opens the price fields on what is already known, so a correction starts from it. */
        fun startEditingPrice() {
            val position = uiState.value.position
            val quote = position?.quote
            priceForm.value =
                PriceEntryState(
                    editing = true,
                    input = quote?.let { AmountEntry.fromMinorUnits(it.price.minorUnits) } ?: AmountEntry.EMPTY,
                    date = quote?.asOf?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: today(),
                )
        }

        fun cancelEditingPrice() {
            priceForm.value = PriceEntryState()
        }

        fun onPriceTyped(typed: String) {
            priceForm.update { it.copy(input = AmountEntry.sanitize(typed)) }
        }

        /**
         * Switches between typing a unit price and typing what the whole position is worth,
         * converting what is already there so the number on screen keeps meaning the same money.
         */
        fun onPriceBasisChanged(basis: PriceBasis) {
            val quantity = uiState.value.position?.quantity ?: return
            priceForm.update { form ->
                if (form.basis == basis || form.input.isEmpty()) {
                    form.copy(basis = basis)
                } else {
                    val typed = Money(AmountEntry.toMinorUnits(form.input), CURRENCY_PLACEHOLDER)
                    val converted =
                        when (basis) {
                            PriceBasis.TOTAL -> (quantity * typed).minorUnits
                            PriceBasis.PER_UNIT -> unitPriceFrom(typed.minorUnits, quantity)
                        }
                    form.copy(basis = basis, input = AmountEntry.fromMinorUnits(converted))
                }
            }
        }

        fun openPriceDatePicker() {
            priceForm.update { it.copy(pickingDate = true) }
        }

        fun onPriceDateSelected(date: LocalDate) {
            priceForm.update { it.copy(date = date, pickingDate = false) }
        }

        fun dismissPriceDatePicker() {
            priceForm.update { it.copy(pickingDate = false) }
        }

        /**
         * Stores the typed price. The instant is the *start of the chosen day* in the phone's
         * zone rather than "now": a price dated yesterday must not sort as if it arrived this
         * second, or its age — the one thing that says how much to trust it — would be wrong.
         */
        fun savePrice() {
            val position = uiState.value.position ?: return
            val holding = position.holding
            val form = priceForm.value
            if (form.input.isEmpty()) return
            val typed = AmountEntry.toMinorUnits(form.input)
            // Whichever way it was typed, one price per unit is what is stored: a total is a
            // fact about a quantity, and a quantity changes the next time anything is traded.
            val minorUnits =
                when (form.basis) {
                    PriceBasis.PER_UNIT -> typed
                    PriceBasis.TOTAL -> unitPriceFrom(typed, position.quantity)
                }
            viewModelScope.launch {
                priceQuoteRepository.setManualPrice(
                    symbol = holding.symbol,
                    price = Money(minorUnits, holding.currency),
                    asOf = form.date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                )
                priceForm.value = PriceEntryState()
            }
        }

        fun requestDeletion() {
            removal.value = true
        }

        fun cancelDeletion() {
            removal.value = false
        }

        fun confirmDeletion() {
            viewModelScope.launch {
                holdingRepository.deleteHolding(holdingId)
                removal.value = false
            }
        }

        fun today(): LocalDate = LocalDate.now(clock)

        /**
         * The price one unit must be for [totalMinor] to be what the whole position is worth.
         *
         * Through [BigInteger] for the reason `Quantity.times` is: the numerator is a value in
         * cents multiplied by the eight-decimal scale, which leaves a `Long` behind at figures
         * this app will really see. A position of nothing has no unit price, so it keeps what
         * was typed rather than dividing by zero.
         */
        private fun unitPriceFrom(
            totalMinor: Long,
            quantity: Quantity,
        ): Long =
            if (quantity.isZero) {
                totalMinor
            } else {
                divideRoundingHalfUp(
                    BigInteger.valueOf(totalMinor) * BigInteger.valueOf(Quantity.SCALE),
                    BigInteger.valueOf(quantity.scaled),
                )
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L

            /**
             * The currency is irrelevant while converting between two ways of writing the same
             * number: both sides are the holding's own, and nothing here leaves this file.
             */
            const val CURRENCY_PLACEHOLDER = "XXX"
        }
    }

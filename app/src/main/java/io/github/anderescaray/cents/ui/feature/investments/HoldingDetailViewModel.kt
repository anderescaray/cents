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

/** The price being typed, which is form state rather than something the database knows yet. */
@Immutable
data class PriceEntryState(
    val editing: Boolean = false,
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
            val holding = uiState.value.position?.holding ?: return
            val form = priceForm.value
            if (form.input.isEmpty()) return
            val minorUnits = AmountEntry.toMinorUnits(form.input)
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

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

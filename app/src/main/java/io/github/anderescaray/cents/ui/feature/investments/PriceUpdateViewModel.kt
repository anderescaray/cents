package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.repository.PriceQuoteRepository
import io.github.anderescaray.cents.ui.util.AmountEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One holding, and what the owner is typing for it. */
@Immutable
data class PriceRow(
    val symbol: String,
    val name: String,
    val currency: String,
    /** What is stored now, and null when nothing has ever been typed for it. */
    val current: Money?,
    /** When that price was true, so a stale one can say so. */
    val asOf: Instant?,
    /** What is being typed, in [AmountEntry]'s canonical form. Empty means "leave it alone". */
    val input: String = AmountEntry.EMPTY,
) {
    val hasNewPrice: Boolean get() = input.isNotEmpty() && AmountEntry.toMinorUnits(input) > 0
}

@Immutable
data class PriceUpdateUiState(
    val rows: List<PriceRow> = emptyList(),
    /**
     * The day every price on this screen was true.
     *
     * **One date for all of them**, because they are read in one sitting from one broker screen.
     * A date per row would be more precise about something nobody is imprecise about, and it
     * would put a calendar between the owner and every number they came here to type.
     */
    val date: LocalDate = LocalDate.EPOCH,
    val today: LocalDate = LocalDate.EPOCH,
    val pickingDate: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && rows.isEmpty()

    /** How many prices are about to be written, which is what the button says. */
    val changedCount: Int get() = rows.count { it.hasNewPrice }

    val canSave: Boolean get() = changedCount > 0 && !saving
}

/**
 * Every price on one screen.
 *
 * The portfolio has priced holdings one at a time since #44, which is right when a single number
 * has changed and tedious when the owner has just opened their broker and is reading all of them.
 * Until a provider can carry the listings this portfolio actually holds — and **none of the free
 * tiers can** (`DECISIONS.md` D20) — typing them is not a fallback, it is the way prices get in.
 * So it is worth being quick.
 *
 * **Only open positions appear.** A holding that has been sold has no quantity, so a price for it
 * would be a number about nothing (D17).
 *
 * **A row left empty is left alone**, rather than read as zero. Half the point of this screen is
 * updating three prices out of eight without disturbing the other five, and a blank field is how
 * somebody says *not this one*.
 */
@HiltViewModel
class PriceUpdateViewModel
    @Inject
    constructor(
        private val portfolioRepository: PortfolioRepository,
        private val priceQuoteRepository: PriceQuoteRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val today = LocalDate.now(clock)
        private val state = MutableStateFlow(PriceUpdateUiState(date = today, today = today))
        val uiState: StateFlow<PriceUpdateUiState> = state.asStateFlow()

        init {
            viewModelScope.launch {
                // Read once rather than observed: this screen is a form, and a portfolio that
                // re-emitted while somebody was halfway through typing would take the half-typed
                // number with it.
                val portfolio = portfolioRepository.observePortfolio().first()
                state.update { it.copy(rows = portfolio.openPositions.map(::rowFor), loaded = true) }
            }
        }

        private fun rowFor(position: Position) =
            PriceRow(
                symbol = position.holding.symbol,
                name = position.holding.name,
                currency = position.holding.currency,
                current = position.usableQuote?.price,
                asOf = position.usableQuote?.asOf,
            )

        fun onTyped(
            symbol: String,
            input: String,
        ) = state.update { current ->
            current.copy(
                rows =
                    current.rows.map { row ->
                        if (row.symbol == symbol) row.copy(input = AmountEntry.sanitize(input)) else row
                    },
            )
        }

        fun openDatePicker() = state.update { it.copy(pickingDate = true) }

        fun dismissDatePicker() = state.update { it.copy(pickingDate = false) }

        fun setDate(date: LocalDate) = state.update { it.copy(date = date, pickingDate = false) }

        /**
         * Writes every price that was typed, and nothing else.
         *
         * A price is stored against the **symbol** rather than the holding, so two accounts
         * holding the same instrument cannot disagree about what it is worth — the same rule
         * #44 set, reached from a different screen.
         */
        fun save() {
            val current = state.value
            if (!current.canSave) return
            state.update { it.copy(saving = true) }
            viewModelScope.launch {
                val asOf = current.date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                current.rows.filter { it.hasNewPrice }.forEach { row ->
                    priceQuoteRepository.setManualPrice(
                        symbol = row.symbol,
                        price = Money(AmountEntry.toMinorUnits(row.input), row.currency),
                        asOf = asOf,
                    )
                }
                state.update { it.copy(saving = false, finished = true) }
            }
        }
    }

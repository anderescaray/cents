package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.repository.HoldingValueRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
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

/** One asset on the values screen, and what has been typed for it. */
@Immutable
data class ValueEntry(
    val position: Position,
    val input: String = AmountEntry.EMPTY,
) {
    val typedMinor: Long? get() = input.takeIf { it != AmountEntry.EMPTY }?.let(AmountEntry::toMinorUnits)

    /** What the typed value changes since the asset's current worth, or null with nothing typed. */
    val change: Money? get() = typedMinor?.let { Money(it - position.value.minorUnits, position.holding.currency) }
}

@Immutable
data class ValueUpdateUiState(
    val entries: List<ValueEntry> = emptyList(),
    val date: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val pickingDate: Boolean = false,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    val canSave: Boolean get() = !saving && entries.any { it.typedMinor != null }
}

/**
 * The week's values (D40): what each open asset in a broker is worth now, all of it. An asset left
 * empty keeps its last value, which is exactly what a week without one means.
 */
@HiltViewModel
class ValueUpdateViewModel
    @Inject
    constructor(
        private val portfolio: PortfolioRepository,
        private val values: HoldingValueRepository,
        clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<Destination.ValueUpdate>()
        private val today = LocalDate.now(clock)
        private val form = MutableStateFlow(ValueUpdateUiState(date = today, today = today))

        val uiState: StateFlow<ValueUpdateUiState> = form.asStateFlow()

        init {
            viewModelScope.launch {
                val positions =
                    portfolio.observePositions().first().filter { position ->
                        position.isOpen &&
                            position.holding.accountId == route.accountId &&
                            (route.holdingId == Destination.ValueUpdate.ALL || position.holding.id == route.holdingId)
                    }
                form.update { it.copy(entries = positions.map(::ValueEntry)) }
            }
        }

        fun onValueTyped(
            holdingId: Long,
            value: String,
        ) = form.update { state ->
            state.copy(
                entries =
                    state.entries.map {
                        if (it.position.holding.id == holdingId) it.copy(input = AmountEntry.sanitize(value)) else it
                    },
            )
        }

        fun openDatePicker() = form.update { it.copy(pickingDate = true) }

        fun dismissDatePicker() = form.update { it.copy(pickingDate = false) }

        fun onDateSelected(value: LocalDate) = form.update { it.copy(date = value, pickingDate = false) }

        fun dismissWriteFailure() = form.update { it.copy(writeFailed = false) }

        fun save() {
            val state = form.value
            if (!state.canSave) return
            form.update { it.copy(saving = true, writeFailed = false) }
            viewModelScope.launch {
                val written =
                    runCatching {
                        state.entries.forEach { entry ->
                            val typed = entry.typedMinor ?: return@forEach
                            val holding = entry.position.holding
                            values.setValue(holding.id, state.date, Money(typed, holding.currency))
                        }
                    }
                form.update { it.copy(saving = false, writeFailed = written.isFailure, finished = written.isSuccess) }
            }
        }
    }

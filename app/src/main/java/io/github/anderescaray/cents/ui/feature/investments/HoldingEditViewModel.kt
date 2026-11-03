package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.holdsInvestments
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.HoldingRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.AmountEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class HoldingEditUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val assetClass: AssetClass = AssetClass.INDEX_FUND,
    val accountId: Long? = null,
    /** The brokers a new asset can go in; an existing one stays where it is. */
    val accounts: List<Account> = emptyList(),
    val accountsLoaded: Boolean = false,
    /** The first purchase, for a new asset. */
    val amountInput: String = AmountEntry.EMPTY,
    val date: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val pickingDate: Boolean = false,
    val note: String = "",
    val tradeCount: Int = 0,
    val deleting: Boolean = false,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }

    val needsBrokerAccount: Boolean get() = isNew && accountsLoaded && accounts.isEmpty()

    val amountMinor: Long get() = AmountEntry.toMinorUnits(amountInput)

    val canSave: Boolean
        get() = name.isNotBlank() && !saving && (!isNew || (account != null && amountMinor > 0))
}

/**
 * A new asset with its first purchase, or an existing one's type, name and note (D40). The name is
 * the owner's own and matches nothing, so there is nothing to check it against.
 */
@HiltViewModel
class HoldingEditViewModel
    @Inject
    constructor(
        private val holdingRepository: HoldingRepository,
        private val accountRepository: AccountRepository,
        clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<Destination.HoldingEdit>()
        private val holdingId: Long? = route.holdingId.takeIf { it != Destination.HoldingEdit.NEW }
        private val today = LocalDate.now(clock)

        private val form =
            MutableStateFlow(
                HoldingEditUiState(
                    isNew = holdingId == null,
                    accountId = route.accountId.takeIf { it != Destination.HoldingEdit.NO_ACCOUNT },
                    date = today,
                    today = today,
                ),
            )

        val uiState: StateFlow<HoldingEditUiState> = form.asStateFlow()

        private var existing: Holding? = null

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val holding = holdingId?.let { holdingRepository.findById(it) }
            existing = holding
            if (holding != null) {
                val trades = holdingRepository.countTrades(holding.id)
                form.update {
                    it.copy(
                        name = holding.name,
                        assetClass = holding.assetClass,
                        accountId = holding.accountId,
                        note = holding.note.orEmpty(),
                        tradeCount = trades,
                    )
                }
            }
            // Watched rather than read once: a broker made from this form, when there was none,
            // has to appear in it on the way back.
            accountRepository.observeAccounts(includeArchived = true).collect { all ->
                form.update { state ->
                    val offered = all.filter { it.holdsInvestments || it.id == holding?.accountId }
                    val kept = state.accountId?.takeIf { id -> offered.any { it.id == id } }
                    state.copy(accounts = offered, accountsLoaded = true, accountId = kept ?: offered.firstOrNull()?.id)
                }
            }
        }

        fun onNameChanged(value: String) = form.update { it.copy(name = value) }

        fun onAssetClassChanged(value: AssetClass) = form.update { it.copy(assetClass = value) }

        fun onAccountChanged(value: Long) = form.update { it.copy(accountId = value) }

        fun onAmountTyped(value: String) = form.update { it.copy(amountInput = AmountEntry.sanitize(value)) }

        fun onNoteChanged(value: String) = form.update { it.copy(note = value) }

        fun openDatePicker() = form.update { it.copy(pickingDate = true) }

        fun dismissDatePicker() = form.update { it.copy(pickingDate = false) }

        fun onDateSelected(value: LocalDate) = form.update { it.copy(date = value, pickingDate = false) }

        fun dismissWriteFailure() = form.update { it.copy(writeFailed = false) }

        fun requestDeletion() = form.update { it.copy(deleting = true) }

        fun cancelDeletion() = form.update { it.copy(deleting = false) }

        fun confirmDeletion() {
            val id = holdingId ?: return
            form.update { it.copy(deleting = false, saving = true) }
            viewModelScope.launch {
                val done = runCatching { holdingRepository.deleteHolding(id) }
                form.update { it.copy(saving = false, writeFailed = done.isFailure, finished = done.isSuccess) }
            }
        }

        fun save() {
            val state = form.value
            if (!state.canSave) return
            form.update { it.copy(saving = true, writeFailed = false) }
            viewModelScope.launch {
                val written = runCatching { write(state) }
                form.update { it.copy(saving = false, writeFailed = written.isFailure, finished = written.isSuccess) }
            }
        }

        private suspend fun write(state: HoldingEditUiState) {
            val note = state.note.trim().ifBlank { null }
            val old = existing
            if (old != null) {
                holdingRepository.updateHolding(
                    old.copy(name = state.name.trim(), assetClass = state.assetClass, note = note),
                )
                return
            }
            // The account object, not the id: its currency is the asset's (rule 2).
            val account = state.account ?: return
            holdingRepository.createHolding(
                holding =
                    Holding(
                        id = 0,
                        accountId = account.id,
                        assetClass = state.assetClass,
                        name = state.name.trim(),
                        currency = account.currency,
                        note = note,
                    ),
                firstPurchase =
                    Trade(
                        id = 0,
                        holdingId = 0,
                        side = TradeSide.BUY,
                        amount = Money(state.amountMinor, account.currency),
                        date = state.date,
                        note = null,
                    ),
            )
        }
    }

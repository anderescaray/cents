package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.HoldingRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@Immutable
data class HoldingEditUiState(
    val isNew: Boolean = true,
    val symbol: String = "",
    val name: String = "",
    val assetClass: AssetClass = AssetClass.STOCK,
    val accountId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val note: String = "",
    /** The symbol is already held in the chosen account: saving would split one position in two. */
    val duplicateOf: String? = null,
    val saving: Boolean = false,
    /** The database refused the write. It is shown and nothing is retried automatically. */
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    /** The chosen account, or null while the list is still loading. */
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }

    /**
     * Saving needs an account that is actually **in the list**, not merely an id.
     * A holding in an archived account used to satisfy `accountId != null` while no row was
     * selected, and the currency then fell through to a hard-coded default — which on a
     * non-EUR device silently relabelled the holding (rule 2).
     */
    val canSave: Boolean
        get() = symbol.isNotBlank() && name.isNotBlank() && account != null && duplicateOf == null && !saving
}

/**
 * Creating or editing one holding: what it is, what kind of thing it is, and which account it
 * is held in.
 *
 * **The symbol is the interesting field.** It is what a price is stored against, so it is
 * upper-cased on the way in and checked against the account before saving: two rows for NVDA in
 * one account would split a single position, and every average and total would then be right
 * about each half and wrong about the holding. The database refuses it too — there is a unique
 * index — but asking first is what lets the form say *why* instead of throwing.
 *
 * The currency is the device's and is not asked for, exactly as the account form no longer asks
 * (D2). The instrument's own currency is a real thing and the column is there for it, but a
 * dollar-priced holding needs a conversion rate to sit in a euro total, and that rate is the
 * subject of PRD 4.5 rather than something to invent here.
 */
@HiltViewModel
class HoldingEditViewModel
    @Inject
    constructor(
        private val holdingRepository: HoldingRepository,
        private val accountRepository: AccountRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val holdingId: Long? =
            savedStateHandle
                .toRoute<Destination.HoldingEdit>()
                .holdingId
                .takeIf { it != Destination.HoldingEdit.NEW }

        private val form = MutableStateFlow(HoldingEditUiState(isNew = holdingId == null))

        val uiState: StateFlow<HoldingEditUiState> = form.asStateFlow()

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            // Archived accounts are offered here, the way they are counted in the portfolio and
            // offered in the Reports picker: a holding does not stop existing because the account
            // it lives in was put away, and leaving it out of the list left the form unable to
            // name the account its own holding is in.
            val accounts = accountRepository.observeAccounts(includeArchived = true).first()
            val holding = holdingId?.let { holdingRepository.findById(it) }
            form.update { state ->
                state.copy(
                    symbol = holding?.symbol ?: state.symbol,
                    name = holding?.name ?: state.name,
                    assetClass = holding?.assetClass ?: state.assetClass,
                    // A broker account is the obvious home for a holding, so it is offered
                    // first — but any account is allowed, because what the owner calls the
                    // account they invest from is theirs to decide.
                    accountId =
                        holding?.accountId ?: accounts.firstOrNull { it.type == AccountType.BROKER }?.id
                            ?: accounts.firstOrNull()?.id,
                    accounts = accounts,
                    note = holding?.note.orEmpty(),
                )
            }
            checkForDuplicate()
        }

        fun onSymbolChanged(value: String) {
            form.update { it.copy(symbol = value.uppercase(Locale.ROOT).trim()) }
            checkForDuplicate()
        }

        fun dismissWriteFailure() {
            form.update { it.copy(writeFailed = false) }
        }

        fun onNameChanged(value: String) {
            form.update { it.copy(name = value) }
        }

        fun onAssetClassChanged(value: AssetClass) {
            form.update { it.copy(assetClass = value) }
        }

        fun onAccountChanged(value: Long) {
            form.update { it.copy(accountId = value) }
            checkForDuplicate()
        }

        fun onNoteChanged(value: String) {
            form.update { it.copy(note = value) }
        }

        /**
         * Is this symbol already held in this account?
         *
         * **One check at a time.** It runs on every keystroke, and unsequenced coroutines finish
         * in whatever order the database answers — so a stale reply could clear the warning after
         * a duplicate had been typed, and `save()` would then walk into the unique index it
         * exists to keep away from. Cancelling the one in flight makes the last answer the last
         * word.
         */
        private fun checkForDuplicate() {
            val state = form.value
            val accountId = state.accountId
            duplicateCheck?.cancel()
            if (accountId == null || state.symbol.isBlank()) {
                form.update { it.copy(duplicateOf = null) }
                return
            }
            duplicateCheck =
                viewModelScope.launch {
                    val existing = holdingRepository.findBySymbol(accountId, state.symbol)
                    val clashes = existing != null && existing.id != holdingId
                    form.update { it.copy(duplicateOf = if (clashes) existing?.symbol else null) }
                }
        }

        fun save() {
            val state = form.value
            // The account object, not the id: its currency is what the holding is denominated
            // in, and there is no sensible fallback for a currency (rule 2).
            val account = state.account ?: return
            if (!state.canSave) return
            form.update { it.copy(saving = true, writeFailed = false) }
            viewModelScope.launch {
                val holding =
                    Holding(
                        id = holdingId ?: 0L,
                        accountId = account.id,
                        assetClass = state.assetClass,
                        symbol = state.symbol,
                        name = state.name.trim(),
                        currency = account.currency,
                        note = state.note.trim().ifBlank { null },
                    )
                // The duplicate check ahead of this is a courtesy, not a guarantee: the row it
                // looked for can be created between the check and the write, and the unique
                // index will then refuse this one. An exception escaping here would take the app
                // down, so it becomes an alert — the same bargain `AccountRemoval` strikes.
                val written =
                    runCatching {
                        if (holdingId == null) {
                            holdingRepository.createHolding(holding)
                        } else {
                            holdingRepository.updateHolding(holding)
                        }
                    }
                form.update {
                    it.copy(saving = false, writeFailed = written.isFailure, finished = written.isSuccess)
                }
            }
        }

        private var duplicateCheck: Job? = null
    }

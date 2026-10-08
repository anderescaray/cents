package io.github.anderescaray.cents.ui.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.BalanceChange
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.BalanceRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** What the owner has typed and chosen so far, before it meets the account. */
data class BalanceForm(
    /** Typed in euros, comma and all — the rules are [AmountEntry]'s. */
    val input: String = AmountEntry.EMPTY,
    val negative: Boolean = false,
    /** Null until the owner picks one: there is no default (`docs/next-improvements.md` §7). */
    val change: BalanceChange? = null,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
)

data class AccountBalanceUiState(
    /** Null while loading, and once the account is gone. */
    val account: AccountWithBalance? = null,
    val form: BalanceForm = BalanceForm(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
) {
    // Everything below is worked out once, when the view model builds the state, rather than each
    // time a composable reads it (CLAUDE.md §5).

    /** The account's currency; null until it is read, because no other currency is right (rule 2). */
    val currency: String? = account?.account?.currency

    /** Nothing typed is not a target of zero: zero is typed as *0*. */
    val hasTarget: Boolean = form.input != AmountEntry.EMPTY

    /**
     * Typed in euros, but a balance is minor units like every amount in the app (rule 1). Null
     * until the account says which currency it is in.
     */
    val target: Money? =
        currency?.let { currency ->
            val magnitude = AmountEntry.toMinorUnits(form.input)
            Money(if (form.negative) -magnitude else magnitude, currency)
        }

    /**
     * What the account holds now, live: it moves if a payment is captured while this screen is
     * open, and the difference moves with it.
     */
    val current: Money? = account?.balance

    /** Target minus what the account holds now; null until a target is typed. */
    val difference: Money? = if (hasTarget && target != null && current != null) target - current else null

    /** The opening balance a rebase would leave, for the option that says so. */
    val rebasedOpening: Money? = difference?.let { difference -> account?.account?.openingBalance?.plus(difference) }

    /** The opening balance today, which a rebase moves from. */
    val opening: Money? = account?.account?.openingBalance

    val alreadyThere: Boolean = difference?.isZero == true

    /** The difference each way's description names; none while there is nothing to change. */
    val pendingDifference: Money? = difference?.takeUnless { it.isZero }

    /** A typed balance below zero, drawn in red like any balance in the red. */
    val targetIsNegative: Boolean = target?.isNegative == true

    val canSave: Boolean = difference?.isZero == false && form.change != null && !form.saving
}

/**
 * Setting one account's balance to what it really holds, when movements were missed.
 *
 * The owner says the balance, and picks — every time, with no default — whether the earlier
 * balances stay as they were or move with it (`BalanceChange`). Neither creates an income or an
 * expense, so nothing here can change a report, a budget or what was saved.
 */
@HiltViewModel
class AccountBalanceViewModel
    @Inject
    constructor(
        accountRepository: AccountRepository,
        private val balanceRepository: BalanceRepository,
        private val today: Today,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val accountId: Long = savedStateHandle.toRoute<Destination.AccountBalance>().accountId

        private val form = MutableStateFlow(BalanceForm())

        val uiState: StateFlow<AccountBalanceUiState> =
            combine(
                accountRepository.observeAccountWithBalance(accountId),
                form,
                today.date,
            ) { account, form, day ->
                AccountBalanceUiState(account = account, form = form, today = day, isLoading = false)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AccountBalanceUiState(),
            )

        fun appendDigit(digit: Char) {
            if (!digit.isDigit()) return
            edit { it.copy(input = AmountEntry.append(it.input, digit)) }
        }

        /** The comma key. A second one, or a third digit behind it, simply does not go in. */
        fun appendDecimalSeparator() = edit { it.copy(input = AmountEntry.append(it.input, AmountEntry.SEPARATOR)) }

        fun deleteDigit() = edit { it.copy(input = AmountEntry.delete(it.input)) }

        /** A card, or an overdraft, can really be in the red. */
        fun toggleSign() = edit { it.copy(negative = !it.negative) }

        fun choose(change: BalanceChange) = edit { it.copy(change = change) }

        fun save() {
            val state = uiState.value
            val change = state.form.change
            val target = state.target
            if (!state.canSave || change == null || target == null) return
            form.update { it.copy(saving = true, writeFailed = false) }

            viewModelScope.launch {
                // The difference shown is only what the screen knew a moment ago. The one written
                // is measured again inside the write, so the account ends at the typed balance
                // even if a payment was captured in between.
                // Cancellation is let through rather than reported as a failed write: the screen is
                // going, and there is nobody left to read the footer.
                val written =
                    try {
                        balanceRepository.setBalance(accountId, target, change, on = today.date.value)
                        true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                form.update { it.copy(saving = false, writeFailed = !written, finished = written) }
            }
        }

        private fun edit(change: (BalanceForm) -> BalanceForm) {
            form.update { change(it).copy(writeFailed = false) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

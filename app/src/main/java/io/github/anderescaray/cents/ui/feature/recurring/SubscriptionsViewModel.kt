package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PendingCharge
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.usecase.SubscriptionTotals
import io.github.anderescaray.cents.domain.usecase.nextOccurrenceAfter
import io.github.anderescaray.cents.domain.usecase.subscriptionTotalsOf
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One rule as a row: what it is, what it costs, and when it next comes round. */
@Immutable
data class SubscriptionRow(
    val rule: RecurringRule,
    val nextDue: LocalDate?,
) {
    val id: Long get() = rule.id
}

@Immutable
data class SubscriptionsUiState(
    val active: List<SubscriptionRow> = emptyList(),
    val stopped: List<SubscriptionRow> = emptyList(),
    /** Variable-amount occurrences waiting for a figure, oldest first. */
    val pending: List<PendingCharge> = emptyList(),
    val today: LocalDate = LocalDate.EPOCH,
    val totals: SubscriptionTotals = SubscriptionTotals(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && active.isEmpty() && stopped.isEmpty()

    /**
     * What the active charges come to per month, and null when they do not share a currency.
     *
     * **This is the figure that makes the screen worth having** (PRD §4.4): a plain list of
     * subscriptions adds nothing over the movements that are already recorded. Knowing they come
     * to 84 € a month is the thing nobody works out by hand.
     *
     * Income is excluded rather than netted off. A salary is a recurring rule too, and adding it
     * in would produce a cheerful number that answers no question — what is wanted here is what
     * is going *out*.
     */
    val monthlyOutgoing: Money? get() = totals.monthlyOutgoing

    /**
     * Whether a guess is in that figure.
     *
     * A variable rule contributes the amount it was set up expecting, because leaving it out
     * would understate what the month is committed to by exactly the bills that are hardest to
     * predict. It is still a guess, so the caption says so rather than letting the number look
     * like arithmetic.
     */
    val includesEstimates: Boolean get() = totals.includesEstimates

    val yearlyOutgoing: Money? get() = totals.yearlyOutgoing
}

/**
 * The subscriptions screen: every rule, and above all what they cost together.
 *
 * Stopped rules are kept and listed below the active ones rather than hidden. "Cancel Netflix"
 * deactivates the rule (PRD §4.4), and a rule that has vanished from every screen is one nobody
 * can see the history of — or start again in December.
 */
@HiltViewModel
class SubscriptionsViewModel
    @Inject
    constructor(
        private val repository: RecurringRuleRepository,
        today: Today,
    ) : ViewModel() {
        val uiState: StateFlow<SubscriptionsUiState> =
            combine(
                repository.observeRules(),
                repository.observePending(),
                // The day is an input, not something read once: an app left open over midnight
                // on the last of the month has to move its "committed this month" to the new one.
                today.date,
            ) { rules, pending, today ->
                val rows = rules.map { SubscriptionRow(it, it.nextOccurrenceAfter(today)) }
                SubscriptionsUiState(
                    active = rows.filter { it.rule.active },
                    stopped = rows.filterNot { it.rule.active },
                    pending = pending,
                    today = today,
                    totals = subscriptionTotalsOf(rules, today),
                    isLoading = false,
                )
            }.flowOn(Dispatchers.Default).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SubscriptionsUiState(),
            )

        /** Stops a rule, or starts it again. Nothing it has already made is touched. */
        fun setActive(
            id: Long,
            active: Boolean,
        ) {
            viewModelScope.launch { repository.setActive(id, active) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

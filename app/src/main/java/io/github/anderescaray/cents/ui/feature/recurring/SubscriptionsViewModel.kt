package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.usecase.nextOccurrenceAfter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
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
    val today: LocalDate = LocalDate.EPOCH,
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
    val monthlyOutgoing: Money? get() = totalOf { it.monthlyCost }

    val yearlyOutgoing: Money? get() = totalOf { it.yearlyCost }

    private fun totalOf(cost: (RecurringRule) -> Money): Money? {
        val charges = active.map { it.rule }.filter { it.kind == TransactionKind.EXPENSE }
        val currency = charges.map { it.amount.currency }.distinct().singleOrNull() ?: return null
        return charges.fold(Money.zero(currency)) { sum, rule -> sum + cost(rule) }
    }
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
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<SubscriptionsUiState> =
            repository
                .observeRules()
                .map { rules ->
                    val today = LocalDate.now(clock)
                    val rows = rules.map { SubscriptionRow(it, it.nextOccurrenceAfter(today)) }
                    SubscriptionsUiState(
                        active = rows.filter { it.rule.active },
                        stopped = rows.filterNot { it.rule.active },
                        today = today,
                        isLoading = false,
                    )
                }.stateIn(
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

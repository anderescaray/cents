package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsCard
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwipeableRow
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.LocalDate

/**
 * Everything that repeats, and above all **what it comes to**.
 *
 * The total at the top is the screen's reason to exist (PRD §4.4). A list of subscriptions on
 * its own says nothing the movement list does not already say; the answer nobody works out by
 * hand is that the eleven of them come to 84 € a month.
 *
 * Stopped rules stay, below the active ones. "Cancel Netflix" deactivates a rule rather than
 * removing it, so that the charges it made keep the name of the thing that explains them — and
 * so that starting it again in December is one tap rather than typing it all out afresh.
 */
@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    onNewRule: () -> Unit,
    onOpenPending: () -> Unit,
    onEditRule: (Long) -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SubscriptionsScreen(
        state = state,
        onBack = onBack,
        onNewRule = onNewRule,
        onOpenPending = onOpenPending,
        onEditRule = onEditRule,
        onSetActive = viewModel::setActive,
    )
}

@Composable
fun SubscriptionsScreen(
    state: SubscriptionsUiState,
    onBack: () -> Unit,
    onNewRule: () -> Unit,
    onOpenPending: () -> Unit,
    onEditRule: (Long) -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.subscriptions_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.tab_settings), onClick = onBack) },
        trailing = { AddRuleButton(onClick = onNewRule) },
    ) {
        if (state.isLoading) return@CentsLargeTitleScaffold

        if (state.isEmpty) {
            emptyState()
            return@CentsLargeTitleScaffold
        }

        // Above the total, because it is the only thing on this screen that is asking for
        // something rather than reporting it.
        waitingSection(state, onOpenPending)
        totalHeader(state)
        subscriptionSummary(state.totals)
        activeRules(state, onEditRule, onSetActive)
        stoppedRules(state, onEditRule, onSetActive)
    }
}

@Composable
private fun AddRuleButton(onClick: () -> Unit) {
    RoundIconButton(
        iconRes = R.drawable.ic_plus,
        contentDescription = stringResource(R.string.subscriptions_add),
        onClick = onClick,
        modifier = Modifier.testTag(ADD_RULE_TAG),
    )
}

private fun LazyListScope.emptyState() {
    item(key = "empty") {
        CentsListSection(footer = stringResource(R.string.subscriptions_empty_footer)) {
            CentsListRow(
                title = stringResource(R.string.subscriptions_empty),
                showSeparator = false,
            )
        }
    }
}

/**
 * The monthly figure, with the year underneath it.
 *
 * Both, because they answer different questions — *can I afford this every month* and *what is
 * this actually costing me* — and because an annual subscription is invisible in the first and
 * obvious in the second.
 */
private fun LazyListScope.totalHeader(state: SubscriptionsUiState) {
    item(key = "total") {
        val monthly = state.monthlyOutgoing
        CentsCard(modifier = Modifier.padding(horizontal = CentsDimens.screenMargin)) {
            Text(
                text = stringResource(R.string.subscriptions_every_month),
                style = CentsTheme.typography.subhead,
                color = CentsTheme.colors.secondaryLabel,
            )
            Text(
                text = monthly?.let { MoneyFormatter.format(it) } ?: NO_TOTAL,
                style = CentsTheme.typography.display,
                color = CentsTheme.colors.label,
                modifier = Modifier.testTag(MONTHLY_TOTAL_TAG),
            )
            Text(
                text =
                    if (monthly == null) {
                        stringResource(R.string.subscriptions_mixed_currency)
                    } else {
                        stringResource(
                            if (state.includesEstimates) {
                                R.string.subscriptions_total_caption_estimate
                            } else {
                                R.string.subscriptions_total_caption
                            },
                            MoneyFormatter.format(requireNotNull(state.yearlyOutgoing)),
                        )
                    },
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
            )
        }
    }
}

/**
 * The bills the app would not guess.
 *
 * A rule set to *ask me the amount* records nothing until somebody says what it came to, and
 * this is where that is visible — otherwise the honest refusal to invent a number is
 * indistinguishable from the feature quietly not working.
 */
private fun LazyListScope.waitingSection(
    state: SubscriptionsUiState,
    onOpen: () -> Unit,
) {
    if (state.pending.isEmpty()) return
    item(key = "waiting") {
        CentsListSection(
            header = stringResource(R.string.subscriptions_waiting_header),
            footer = stringResource(R.string.subscriptions_waiting_footer),
        ) {
            state.pending.forEachIndexed { index, charge ->
                key(charge.rule.id) {
                    CentsListRow(
                        title = charge.rule.name,
                        subtitle = charge.dueDate.relativeLabel(state.today),
                        value = stringResource(R.string.subscriptions_waiting_value),
                        valueColor = CentsTheme.colors.accent,
                        showChevron = true,
                        showSeparator = index != state.pending.lastIndex,
                        onClick = onOpen,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.activeRules(
    state: SubscriptionsUiState,
    onEdit: (Long) -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
) {
    if (state.active.isEmpty()) return
    item(key = "active") {
        CentsListSection(header = stringResource(R.string.subscriptions_active_header)) {
            state.active.forEachIndexed { index, row ->
                key(row.id) {
                    // Swiping stops rather than deletes, which is the whole difference this
                    // screen has to get right: the charges it made are real money that was
                    // really spent, and a swipe must not reach back into them (rule 8).
                    CentsSwipeableRow(
                        onDelete = { onSetActive(row.id, false) },
                        deleteLabel = stringResource(R.string.subscriptions_stop),
                    ) { rowModifier ->
                        RuleRow(
                            modifier = rowModifier,
                            row = row,
                            today = state.today,
                            last = index == state.active.lastIndex,
                            onClick = { onEdit(row.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.stoppedRules(
    state: SubscriptionsUiState,
    onEdit: (Long) -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
) {
    if (state.stopped.isEmpty()) return
    item(key = "stopped") {
        CentsListSection(
            header = stringResource(R.string.subscriptions_stopped_header),
            footer = stringResource(R.string.subscriptions_stopped_footer),
        ) {
            state.stopped.forEachIndexed { index, row ->
                key(row.id) {
                    CentsSwipeableRow(
                        onDelete = { onSetActive(row.id, true) },
                        deleteLabel = stringResource(R.string.subscriptions_start),
                    ) { rowModifier ->
                        RuleRow(
                            modifier = rowModifier,
                            row = row,
                            today = state.today,
                            last = index == state.stopped.lastIndex,
                            onClick = { onEdit(row.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One rule: its name, what it costs each time, and when it next comes round.
 *
 * The amount shown is the charge itself rather than what it works out to per month. A row
 * saying "Netflix — 4,33 €" for an annual subscription would be arithmetic nobody asked for
 * in the place where the owner is checking they typed the price correctly; the per-month view
 * of it belongs in the total at the top, where it is a comparison rather than a fact.
 */
@Composable
private fun RuleRow(
    row: SubscriptionRow,
    today: LocalDate,
    last: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rule = row.rule
    val frequency = frequencyLabel(rule.frequency, rule.intervalDays)
    val nextDue = row.nextDue
    CentsListRow(
        modifier = modifier,
        title = rule.name,
        subtitle =
            when {
                !rule.active -> {
                    frequency
                }

                nextDue == null -> {
                    stringResource(R.string.subscriptions_finished, frequency)
                }

                else -> {
                    stringResource(
                        if (row.postponed) R.string.subscriptions_next_postponed else R.string.subscriptions_next_due,
                        frequency,
                        nextDue.relativeLabel(today),
                    )
                }
            },
        value = MoneyFormatter.format(rule.amount),
        valueColor =
            if (rule.kind == TransactionKind.INCOME) CentsTheme.colors.positiveText else CentsTheme.colors.label,
        showChevron = true,
        showSeparator = !last,
        onClick = onClick,
    )
}

/** Shown in place of the total when the rules do not share a currency and cannot be added. */
private const val NO_TOTAL = "—"

internal const val ADD_RULE_TAG = "addRule"
internal const val MONTHLY_TOTAL_TAG = "subscriptionsMonthlyTotal"

package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.medium

/**
 * One asset (`DECISIONS.md` D40): what it is worth, what it cost and the difference; the three
 * things done to it — a value, a purchase, a sale; its chart; and everything recorded, newest
 * first.
 */
@Composable
fun AssetScreen(
    onBack: () -> Unit,
    actions: AssetActions,
    viewModel: AssetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssetContent(
        state = state,
        onBack = onBack,
        actions =
            actions.copy(
                onScaleChange = viewModel::onScaleChange,
                onDeleteValue = viewModel::requestValueDeletion,
            ),
    )
    state.deletingValue?.let {
        CentsAlert(
            title = stringResource(R.string.asset_value_delete_title),
            message = stringResource(R.string.asset_value_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::confirmValueDeletion,
            onDismiss = viewModel::cancelValueDeletion,
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
    if (state.deleteFailed) {
        CentsAlert(
            title = stringResource(R.string.delete_failed_title),
            message = stringResource(R.string.asset_value_delete_failed),
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = viewModel::dismissDeleteFailure,
            onDismiss = viewModel::dismissDeleteFailure,
        )
    }
}

/** Where the asset screen leads, and what it asks of its view model. */
data class AssetActions(
    val onEdit: (Long) -> Unit = {},
    /** A value for this asset alone: its account, and the asset. */
    val onUpdateValue: (Long, Long) -> Unit = { _, _ -> },
    val onBuy: (Long) -> Unit = {},
    val onSell: (Long) -> Unit = {},
    val onOpenTrade: (Long, Long) -> Unit = { _, _ -> },
    val onScaleChange: (ChartScale) -> Unit = {},
    val onDeleteValue: (HoldingValue) -> Unit = {},
)

@Composable
internal fun AssetContent(
    state: AssetUiState,
    onBack: () -> Unit = {},
    actions: AssetActions = AssetActions(),
) {
    val position = state.position
    // Deleted from its own edit form: there is nothing left to show, so this goes back too.
    val gone = !state.loading && position == null
    LaunchedEffect(gone) { if (gone) onBack() }
    CentsLargeTitleScaffold(
        title = position?.holding?.name.orEmpty(),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = state.accountName, onClick = onBack) },
    ) {
        if (position == null) return@CentsLargeTitleScaffold
        summary(position)
        doThings(position, actions)
        chartItem(state.points, state.scale, actions.onScaleChange)
        history(position, actions)
    }
}

private fun LazyListScope.summary(position: Position) {
    item(key = "summary") {
        SummarySection(
            lines =
                listOf(
                    SummaryLine(stringResource(R.string.investments_worth), position.value, tag = VALUE_TAG),
                    SummaryLine(stringResource(R.string.investments_put_in), position.cost, tag = COST_TAG),
                    profitLine(position.profit, position.profitRatio),
                ),
            footer =
                when {
                    !position.isOpen -> stringResource(R.string.asset_closed)
                    position.lastValuedOn == null -> stringResource(R.string.asset_no_value)
                    else -> stringResource(R.string.asset_last_value, position.lastValuedOn.medium())
                },
        )
    }
}

/** A value, more bought, a sale; a closed asset can only be bought into again. */
private fun LazyListScope.doThings(
    position: Position,
    actions: AssetActions,
) {
    val holding = position.holding
    item(key = "actions") {
        CentsListSection {
            if (position.isOpen) {
                CentsListRow(
                    title = stringResource(R.string.asset_update_value),
                    showChevron = true,
                    onClick = { actions.onUpdateValue(holding.accountId, holding.id) },
                    modifier = Modifier.testTag(UPDATE_VALUE_TAG),
                )
            }
            CentsListRow(
                title = stringResource(R.string.asset_buy_more),
                showChevron = true,
                onClick = { actions.onBuy(holding.id) },
                modifier = Modifier.testTag(BUY_TAG),
            )
            if (position.isOpen) {
                CentsListRow(
                    title = stringResource(R.string.asset_sell),
                    showChevron = true,
                    onClick = { actions.onSell(holding.id) },
                    modifier = Modifier.testTag(SELL_TAG),
                )
            }
            CentsListRow(
                title = stringResource(R.string.asset_edit),
                showChevron = true,
                showSeparator = false,
                onClick = { actions.onEdit(holding.id) },
            )
        }
    }
}

/** One line of the history: a trade or a value, with the day it happened. */
private sealed interface HistoryEntry {
    val key: String

    data class OfTrade(
        val trade: Trade,
    ) : HistoryEntry {
        override val key: String get() = "trade-${trade.id}"
    }

    data class OfValue(
        val value: HoldingValue,
    ) : HistoryEntry {
        override val key: String get() = "value-${value.id}"
    }
}

/** Newest first; within a day, the reverse of the order the fold applies them in. */
private fun Position.history(): List<HistoryEntry> {
    val byDay =
        (trades.map { it.date } + values.map { it.date }).distinct().sortedDescending()
    return byDay.flatMap { day ->
        trades.filter { it.date == day && it.side == TradeSide.SELL }.sortedByDescending { it.id }.map(
            HistoryEntry::OfTrade,
        ) +
            values.filter { it.date == day }.map(HistoryEntry::OfValue) +
            trades.filter { it.date == day && it.side == TradeSide.BUY }.sortedByDescending { it.id }.map(
                HistoryEntry::OfTrade,
            )
    }
}

private fun LazyListScope.history(
    position: Position,
    actions: AssetActions,
) {
    val entries = position.history()
    val results = position.sales.associate { it.trade.id to it.result }
    item(key = "history") {
        CentsListSection(header = stringResource(R.string.asset_history_header)) {
            entries.forEachIndexed { index, entry ->
                key(entry.key) {
                    val separator = index < entries.lastIndex
                    when (entry) {
                        is HistoryEntry.OfTrade -> {
                            TradeRow(entry.trade, results[entry.trade.id], separator) {
                                actions.onOpenTrade(position.holding.id, entry.trade.id)
                            }
                        }

                        is HistoryEntry.OfValue -> {
                            ValueRow(entry.value, separator) { actions.onDeleteValue(entry.value) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeRow(
    trade: Trade,
    result: Money?,
    showSeparator: Boolean,
    onClick: () -> Unit,
) {
    val title =
        when {
            trade.side == TradeSide.BUY -> stringResource(R.string.asset_history_buy)
            trade.sellsAll -> stringResource(R.string.asset_history_sell_all)
            else -> stringResource(R.string.asset_history_sell)
        }
    val date = trade.date.medium()
    CentsListRow(
        title = title,
        subtitle =
            if (result == null) {
                date
            } else {
                stringResource(R.string.asset_history_result, date, MoneyFormatter.formatSigned(result))
            },
        value = MoneyFormatter.format(trade.amount),
        showChevron = true,
        showSeparator = showSeparator,
        onClick = onClick,
        modifier = Modifier.testTag(HISTORY_TRADE_TAG),
    )
}

/** A typed value. Tapping it offers to delete it: a value is corrected by typing a new one. */
@Composable
private fun ValueRow(
    value: HoldingValue,
    showSeparator: Boolean,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = stringResource(R.string.asset_history_value),
        subtitle = value.date.medium(),
        value = MoneyFormatter.format(value.value),
        valueColor = CentsTheme.colors.secondaryLabel,
        showSeparator = showSeparator,
        onClick = onClick,
        modifier = Modifier.testTag(HISTORY_VALUE_TAG),
    )
}

internal const val VALUE_TAG = "assetValue"
internal const val COST_TAG = "assetCost"
internal const val UPDATE_VALUE_TAG = "assetUpdateValue"
internal const val BUY_TAG = "assetBuy"
internal const val SELL_TAG = "assetSell"
internal const val HISTORY_TRADE_TAG = "historyTrade"
internal const val HISTORY_VALUE_TAG = "historyValue"

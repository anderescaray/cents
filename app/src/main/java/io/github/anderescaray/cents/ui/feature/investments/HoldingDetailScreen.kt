package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.QuantityEntry
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.ZoneId

/**
 * One holding: the position, the price it is valued at, and the trades that produced both.
 *
 * The price row is the honest part of this screen. Until prices come from a provider, every
 * value here rests on a number the owner typed, so the row says when it was true rather than
 * showing it as if it were live — and a holding with no price says so instead of showing zero.
 */
@Composable
fun HoldingDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddTrade: (Long) -> Unit,
    onOpenTrade: (Long, Long) -> Unit,
    viewModel: HoldingDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()
    val deleting by viewModel.pendingDeletion.collectAsStateWithLifecycle()
    val position = state.position

    LaunchedEffect(state.gone) {
        if (state.gone) onBack()
    }

    CentsLargeTitleScaffold(
        title = position?.holding?.name.orEmpty(),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = {
            CentsBackButton(label = stringResource(R.string.tab_investments), onClick = onBack)
        },
        trailing = {
            if (position != null) {
                EditAction(onClick = { onEdit(position.holding.id) })
            }
        },
    ) {
        if (position == null) return@CentsLargeTitleScaffold

        valueHeader(position)
        priceSection(position, price, viewModel)
        positionSection(position)
        tradesSection(state.trades, position, onOpenTrade)

        actions(onAddTrade = { onAddTrade(position.holding.id) }, onDelete = viewModel::requestDeletion)
    }

    if (deleting && position != null) {
        DeleteHoldingAlert(name = position.holding.name, trades = state.trades.size, viewModel = viewModel)
    }

    if (price.pickingDate) {
        CentsDatePicker(
            selected = price.date,
            today = viewModel.today(),
            onSelect = viewModel::onPriceDateSelected,
            onDismiss = viewModel::dismissPriceDatePicker,
        )
    }
}

/** The confirmation, which says how many trades go with the holding (rule 7). */
@Composable
private fun DeleteHoldingAlert(
    name: String,
    trades: Int,
    viewModel: HoldingDetailViewModel,
) {
    CentsAlert(
        title = stringResource(R.string.holding_delete_title, name),
        message =
            if (trades == 0) {
                stringResource(R.string.holding_delete_message)
            } else {
                pluralStringResource(R.plurals.holding_delete_message_trades, trades, trades)
            },
        confirmLabel = stringResource(R.string.action_delete),
        onConfirm = viewModel::confirmDeletion,
        onDismiss = viewModel::cancelDeletion,
        dismissLabel = stringResource(R.string.action_cancel),
        destructive = true,
    )
}

@Composable
private fun EditAction(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.action_edit),
        style = CentsTheme.typography.body,
        color = CentsTheme.colors.accent,
        modifier = Modifier.clickable(onClick = onClick).padding(CentsDimens.spacingXs),
    )
}

private fun LazyListScope.actions(
    onAddTrade: () -> Unit,
    onDelete: () -> Unit,
) {
    item(key = "add-trade") {
        CentsActionRow(label = stringResource(R.string.holding_add_trade), onClick = onAddTrade)
    }
    item(key = "delete") {
        CentsActionRow(label = stringResource(R.string.holding_delete), onClick = onDelete, destructive = true)
    }
}

/** What the position is worth, or the absence of a price said in words rather than in zeros. */
private fun LazyListScope.valueHeader(position: Position) {
    item(key = "value") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text =
                    position.marketValue?.let { MoneyFormatter.format(it) }
                        ?: stringResource(R.string.investments_no_price),
                style = CentsTheme.typography.largeTitle,
                color = CentsTheme.colors.label,
                textAlign = TextAlign.Center,
            )
            Text(
                text =
                    buildString {
                        append(position.holding.symbol)
                        append(" · ")
                        append(assetClassLabel(position.holding.assetClass))
                    },
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
            )
        }
    }
}

private fun LazyListScope.priceSection(
    position: Position,
    price: PriceEntryState,
    viewModel: HoldingDetailViewModel,
) {
    item(key = "price") {
        CentsListSection(
            header = stringResource(R.string.holding_price_header),
            footer = stringResource(R.string.holding_price_manual_footer),
        ) {
            if (price.editing) {
                CentsTextField(
                    value = AmountEntry.toTypedText(price.input),
                    onValueChange = viewModel::onPriceTyped,
                    placeholder = stringResource(R.string.price_dialog_placeholder),
                    numeric = true,
                    showSeparator = true,
                )
                CentsListRow(
                    title = stringResource(R.string.price_dialog_date),
                    value = price.date.relativeLabel(viewModel.today()),
                    onClick = viewModel::openPriceDatePicker,
                )
                CentsListRow(
                    title = stringResource(R.string.action_save),
                    onClick = viewModel::savePrice,
                    showSeparator = true,
                )
                CentsListRow(
                    title = stringResource(R.string.action_cancel),
                    onClick = viewModel::cancelEditingPrice,
                    showSeparator = false,
                )
            } else {
                // The *usable* quote: one stored in another currency values nothing, so showing
                // it as the last price would explain none of what the rows above say.
                val quote = position.usableQuote
                CentsListRow(
                    title = stringResource(R.string.holding_price_row),
                    subtitle =
                        quote?.let {
                            stringResource(
                                R.string.holding_price_asof,
                                it.asOf
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .relativeLabel(),
                            )
                        },
                    value =
                        quote?.let { MoneyFormatter.format(it.price) }
                            ?: stringResource(R.string.investments_no_price),
                    valueColor = if (quote == null) CentsTheme.colors.secondaryLabel else CentsTheme.colors.label,
                    showSeparator = true,
                )
                CentsListRow(
                    title = stringResource(R.string.holding_price_set),
                    onClick = viewModel::startEditingPrice,
                    showSeparator = false,
                )
            }
        }
    }
}

private fun LazyListScope.positionSection(position: Position) {
    item(key = "position") {
        CentsListSection(
            header = stringResource(R.string.holding_position_header),
            footer = if (position.isOpen) null else stringResource(R.string.holding_position_closed),
        ) {
            CentsListRow(
                title = stringResource(R.string.holding_position_quantity),
                value = QuantityEntry.format(position.quantity),
            )
            position.averageCost?.let { average ->
                CentsListRow(
                    title = stringResource(R.string.holding_position_average),
                    value = MoneyFormatter.format(average),
                )
            }
            CentsListRow(
                title = stringResource(R.string.holding_position_cost),
                value = MoneyFormatter.format(position.costBasis),
            )
            position.marketValue?.let { value ->
                CentsListRow(
                    title = stringResource(R.string.holding_position_value),
                    value = MoneyFormatter.format(value),
                )
            }
            position.unrealisedPnl?.let { profit ->
                CentsListRow(
                    title = stringResource(R.string.holding_position_unrealised),
                    value =
                        buildString {
                            append(MoneyFormatter.formatSigned(profit))
                            position.unrealisedReturn?.let { append(" · ").append(formatReturn(it)) }
                        },
                    valueColor = profitColor(profit),
                )
            }
            CentsListRow(
                title = stringResource(R.string.holding_position_realised),
                value = MoneyFormatter.formatSigned(position.realisedPnl),
                valueColor = profitColor(position.realisedPnl),
                showSeparator = false,
            )
        }
    }
}

private fun LazyListScope.tradesSection(
    trades: List<Trade>,
    position: Position,
    onOpenTrade: (Long, Long) -> Unit,
) {
    item(key = "trades") {
        CentsListSection(
            header = stringResource(R.string.holding_trades_header),
            footer = if (trades.isEmpty()) stringResource(R.string.holding_trades_empty) else null,
        ) {
            trades.forEachIndexed { index, trade ->
                key(trade.id) {
                    TradeRow(
                        trade = trade,
                        showSeparator = index < trades.lastIndex,
                        onClick = { onOpenTrade(position.holding.id, trade.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeRow(
    trade: Trade,
    showSeparator: Boolean,
    onClick: () -> Unit,
) {
    val bought = trade.side == TradeSide.BUY
    val quantity = QuantityEntry.format(trade.quantity)
    val price = MoneyFormatter.format(trade.pricePerUnit)
    CentsListRow(
        title = stringResource(if (bought) R.string.holding_trade_buy else R.string.holding_trade_sell, quantity),
        subtitle = trade.date.relativeLabel() + " · " + stringResource(R.string.holding_trade_at, price),
        value = MoneyFormatter.format(trade.net),
        // A sale is money coming back, which is the same green the movement list gives income.
        valueColor = if (bought) CentsTheme.colors.label else CentsTheme.colors.positive,
        showChevron = true,
        showSeparator = showSeparator,
        onClick = onClick,
    )
}

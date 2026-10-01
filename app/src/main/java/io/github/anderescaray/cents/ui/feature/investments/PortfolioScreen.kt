package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.PortfolioTotal
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.QuantityEntry

/**
 * What is held, under one heading per kind of thing.
 *
 * Every number on this screen comes from the trades recorded against a holding and the last
 * price known for it. Nothing is stored as a total, so nothing here can disagree with the
 * history behind it — the same rule that makes an account balance a computed value.
 *
 * A holding with no price yet is listed and says so, rather than being left out or shown as
 * worth nothing. It is still something the owner holds; what is missing is only what it is
 * worth today.
 *
 * A holding that has been sold out of is not in that list and not in the total. It sits at
 * the foot, collapsed, because what it made is recorded and otherwise has nowhere to be read.
 */
@Composable
fun PortfolioScreen(
    onAddHolding: () -> Unit,
    onOpenHolding: (Long) -> Unit,
    onUpdatePrices: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PortfolioScreen(
        state = state,
        onAddHolding = onAddHolding,
        onOpenHolding = onOpenHolding,
        onUpdatePrices = onUpdatePrices,
        onBack = onBack,
    )
}

/**
 * The screen with its state handed to it, so what it renders can be tested without a database
 * behind it — the same split the Reports tab uses.
 */
@Composable
fun PortfolioScreen(
    state: PortfolioUiState,
    onAddHolding: () -> Unit,
    onOpenHolding: (Long) -> Unit,
    onUpdatePrices: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.tab_investments),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.wallet_title), onClick = onBack) },
        trailing = {
            if (state.groups.isNotEmpty()) UpdatePricesButton(onUpdatePrices)
            if (state.hasAccount) AddHoldingButton(onAddHolding)
        },
    ) {
        if (state.loading) return@CentsLargeTitleScaffold

        if (state.isEmpty) {
            emptyState(hasAccount = state.hasAccount)
            return@CentsLargeTitleScaffold
        }

        state.totals.forEach { total ->
            item(key = "total-${total.currency}") {
                TotalSection(total)
            }
        }

        state.groups.forEach { group ->
            item(key = "group-${group.assetClass.name}") {
                CentsListSection(header = assetClassLabel(group.assetClass)) {
                    group.positions.forEachIndexed { index, position ->
                        key(position.holding.id) {
                            PositionRow(
                                position = position,
                                showSeparator = index < group.positions.lastIndex,
                                onClick = { onOpenHolding(position.holding.id) },
                            )
                        }
                    }
                }
            }
        }

        if (state.closedPositions.isNotEmpty()) {
            item(key = "closed-positions") {
                ClosedPositionsSection(
                    positions = state.closedPositions,
                    onOpenHolding = onOpenHolding,
                )
            }
        }
    }
}

/**
 * The totals, one section per currency.
 *
 * There is deliberately no grand total across currencies. Every holding carries the currency
 * its instrument is priced in, and adding dollars to euros needs a rate this app does not have
 * — the same reason the Accounts tab shows no total when its accounts disagree. Today they all
 * agree and there is one section.
 */
@Composable
private fun TotalSection(total: PortfolioTotal) {
    val value = total.marketValue
    val profit = total.profit
    CentsListSection(
        footer =
            total.unpricedCount
                .takeIf { it > 0 }
                ?.let { pluralStringResource(R.plurals.investments_total_unpriced, it, it) },
    ) {
        CentsListRow(
            // With nothing priced there is no value to show, and showing 0,00 € over money that
            // was really spent is the failure a single unpriced row already refuses to commit.
            title =
                value?.let { stringResource(R.string.investments_total_value, MoneyFormatter.format(it)) }
                    ?: stringResource(R.string.investments_no_price),
            subtitle = stringResource(R.string.investments_total_cost, MoneyFormatter.format(total.costBasis)),
            value =
                profit?.let {
                    buildString {
                        append(MoneyFormatter.formatSigned(it))
                        total.profitRatio?.let { ratio -> append(" · ").append(formatReturn(ratio)) }
                    }
                },
            valueColor = profitColor(profit),
            showSeparator = false,
        )
    }
}

@Composable
private fun PositionRow(
    position: Position,
    showSeparator: Boolean,
    onClick: () -> Unit,
) {
    val value = position.marketValue
    CentsListRow(
        title = position.holding.name,
        subtitle =
            buildString {
                append(position.holding.symbol)
                append(" · ")
                append(stringResource(R.string.investments_units, QuantityEntry.format(position.quantity)))
            },
        value = value?.let { MoneyFormatter.format(it) } ?: stringResource(R.string.investments_no_price),
        valueColor = if (value == null) CentsTheme.colors.secondaryLabel else CentsTheme.colors.label,
        showChevron = true,
        showSeparator = showSeparator,
        onClick = onClick,
    )
}

/**
 * Fully sold holdings, collapsed until asked for.
 *
 * One row each, with what the sales made in money and as a percentage of what those units
 * cost. Tapping a row opens the holding that is already there. The totals above do not
 * include these: a closed position is worth nothing, and its profit is already cash.
 */
@Composable
private fun ClosedPositionsSection(
    positions: List<Position>,
    onOpenHolding: (Long) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val stateLabel =
        stringResource(
            if (expanded) R.string.closed_positions_expanded else R.string.closed_positions_collapsed,
        )
    CentsListSection(header = stringResource(R.string.closed_positions_header)) {
        CentsListRow(
            title = pluralStringResource(R.plurals.closed_positions_count, positions.size, positions.size),
            showSeparator = expanded,
            onClick = { expanded = !expanded },
            trailing = { DisclosureChevron(expanded = expanded, stateLabel = stateLabel) },
        )
        if (expanded) {
            positions.forEachIndexed { index, position ->
                key(position.holding.id) {
                    ClosedPositionRow(
                        position = position,
                        showSeparator = index < positions.lastIndex,
                        onClick = { onOpenHolding(position.holding.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClosedPositionRow(
    position: Position,
    showSeparator: Boolean,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = position.holding.name,
        subtitle = position.holding.symbol,
        value = closedPositionValue(position),
        valueColor = profitColor(position.realisedPnl),
        showChevron = true,
        showSeparator = showSeparator,
        onClick = onClick,
    )
}

/** Money, and the percentage when the sold units had a cost to measure it against. */
private fun closedPositionValue(position: Position): String =
    buildString {
        append(MoneyFormatter.formatSigned(position.realisedPnl))
        position.realisedReturn?.let { ratio ->
            append(" · ")
            append(formatReturn(ratio))
        }
    }

/**
 * The disclosure mark: pointing across when the rows are hidden, and down once they are shown.
 *
 * The same chevron the rows use to mean "opens something". Turned, it means the something
 * is already on this screen.
 */
@Composable
private fun DisclosureChevron(
    expanded: Boolean,
    stateLabel: String,
) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = stateLabel,
        tint = CentsTheme.colors.tertiaryLabel,
        modifier =
            Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
    )
}

/**
 * Two different empty states, because they mean different things: nothing held yet is a screen
 * waiting to be filled, and no account at all is a door that leads somewhere else first.
 */
private fun LazyListScope.emptyState(hasAccount: Boolean) {
    item(key = "empty") {
        CentsListSection {
            CentsListRow(
                title = stringResource(R.string.investments_empty_title),
                subtitle =
                    if (hasAccount) {
                        stringResource(R.string.investments_empty_message)
                    } else {
                        stringResource(R.string.investments_empty_no_account)
                    },
                showSeparator = false,
            )
        }
    }
}

internal const val ADD_HOLDING_TAG = "add-holding"

/**
 * The way to every price at once.
 *
 * Offered only with something to price: on an empty portfolio it would open a screen with
 * nothing on it, which is a worse answer than not offering it at all.
 */
@Composable
private fun UpdatePricesButton(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.prices_action),
        style = CentsTheme.typography.body,
        color = CentsTheme.colors.accentText,
        // A bare Text is a few points tall; the tap target is a row's height, as on the
        // movements list.
        modifier =
            Modifier
                .testTag(UPDATE_PRICES_TAG)
                .clip(RoundedCornerShape(CentsDimens.spacingS))
                .tappable(onClick = onClick)
                .defaultMinSize(minHeight = CentsDimens.minRowHeight)
                .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun AddHoldingButton(onClick: () -> Unit) {
    val addLabel = stringResource(R.string.investments_add)
    Box(
        modifier =
            Modifier
                .semantics { contentDescription = addLabel }
                .testTag(ADD_HOLDING_TAG)
                .size(CentsDimens.minRowHeight)
                .clip(CircleShape)
                .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.action_add),
            style = CentsTheme.typography.title2,
            color = CentsTheme.colors.accentText,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The way to every price at once, beside the way to add a holding. */
internal const val UPDATE_PRICES_TAG = "updatePrices"

package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.PortfolioTotal
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
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
 */
@Composable
fun PortfolioScreen(
    onAddHolding: () -> Unit,
    onOpenHolding: (Long) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PortfolioScreen(state = state, onAddHolding = onAddHolding, onOpenHolding = onOpenHolding)
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
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.tab_investments),
        bottomPadding = CentsDimens.sectionSpacing,
        trailing = {
            if (state.hasAccount) {
                val addLabel = stringResource(R.string.investments_add)
                Box(
                    modifier =
                        Modifier
                            .semantics { contentDescription = addLabel }
                            .testTag(ADD_HOLDING_TAG)
                            .size(CentsDimens.minRowHeight)
                            .clip(CircleShape)
                            .clickable(onClick = onAddHolding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.action_add),
                        style = CentsTheme.typography.title2,
                        color = CentsTheme.colors.accent,
                    )
                }
            }
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
    CentsListSection(
        footer =
            total.unpricedCount
                .takeIf { it > 0 }
                ?.let { stringResource(R.string.investments_total_unpriced, it) },
    ) {
        CentsListRow(
            title = stringResource(R.string.investments_total_value, MoneyFormatter.format(total.marketValue)),
            subtitle = stringResource(R.string.investments_total_cost, MoneyFormatter.format(total.costBasis)),
            value =
                buildString {
                    append(MoneyFormatter.formatSigned(total.profit))
                    total.profitRatio?.let { append(" · ").append(formatReturn(it)) }
                },
            valueColor = profitColor(total.profit),
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

package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.InvestmentAccount
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.Sale
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.medium

/**
 * One broker account (`DECISIONS.md` D40): its total, the cash not invested and what is, a chart
 * of the whole, the assets still held, and every closed operation with what it made.
 */
@Composable
fun InvestmentAccountScreen(
    onBack: () -> Unit,
    onAddAsset: (Long) -> Unit,
    onOpenAsset: (Long) -> Unit,
    onUpdateValues: (Long) -> Unit,
    onOpenMovements: (Long) -> Unit,
    viewModel: InvestmentAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InvestmentAccountContent(
        state = state,
        actions =
            InvestmentAccountActions(
                onBack = onBack,
                onAddAsset = onAddAsset,
                onOpenAsset = onOpenAsset,
                onUpdateValues = onUpdateValues,
                onOpenMovements = onOpenMovements,
                onScaleChange = viewModel::onScaleChange,
            ),
    )
}

/** What the screen can be asked to do, in one place so the content takes one parameter for it. */
data class InvestmentAccountActions(
    val onBack: () -> Unit = {},
    val onAddAsset: (Long) -> Unit = {},
    val onOpenAsset: (Long) -> Unit = {},
    val onUpdateValues: (Long) -> Unit = {},
    /** The account itself: its movements, its name, its balance. */
    val onOpenMovements: (Long) -> Unit = {},
    val onScaleChange: (ChartScale) -> Unit = {},
)

@Composable
internal fun InvestmentAccountContent(
    state: InvestmentAccountUiState,
    actions: InvestmentAccountActions = InvestmentAccountActions(),
) {
    val account = state.account
    CentsLargeTitleScaffold(
        title = account?.account?.name.orEmpty(),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.tab_investments), onClick = actions.onBack) },
        trailing = {
            if (account != null) {
                RoundIconButton(
                    iconRes = R.drawable.ic_plus,
                    contentDescription = stringResource(R.string.investments_add),
                    onClick = { actions.onAddAsset(account.account.id) },
                    modifier = Modifier.testTag(ADD_ASSET_TAG),
                )
            }
        },
    ) {
        if (account == null) return@CentsLargeTitleScaffold
        summary(account)
        chartItem(state.points, state.scale, actions.onScaleChange)
        assets(account, actions)
        closedOperations(account, actions.onOpenAsset)
        item(key = "movements") {
            CentsListSection(footer = stringResource(R.string.account_movements_footer)) {
                CentsListRow(
                    title = stringResource(R.string.account_movements),
                    showChevron = true,
                    showSeparator = false,
                    onClick = { actions.onOpenMovements(account.account.id) },
                )
            }
        }
    }
}

private fun LazyListScope.summary(account: InvestmentAccount) {
    item(key = "summary") {
        SummarySection(
            lines =
                listOf(
                    SummaryLine(stringResource(R.string.investments_total), account.total, tag = TOTAL_TAG),
                    SummaryLine(stringResource(R.string.investments_cash), account.cash, tag = CASH_TAG),
                    SummaryLine(stringResource(R.string.investments_invested), account.invested, tag = INVESTED_TAG),
                    profitLine(account.profit, account.profitRatio),
                ),
        )
    }
}

private fun LazyListScope.assets(
    account: InvestmentAccount,
    actions: InvestmentAccountActions,
) {
    val open = account.openPositions
    item(key = "assets") {
        CentsListSection(
            header = stringResource(R.string.account_assets_header),
            footer = if (open.isEmpty()) stringResource(R.string.account_assets_empty) else null,
        ) {
            open.forEach { position ->
                key(position.holding.id) {
                    AssetRow(position, showSeparator = true) { actions.onOpenAsset(position.holding.id) }
                }
            }
            if (open.isNotEmpty()) {
                CentsActionRow(
                    label = stringResource(R.string.account_update_values),
                    onClick = { actions.onUpdateValues(account.account.id) },
                    showSeparator = true,
                    modifier = Modifier.testTag(UPDATE_VALUES_TAG),
                )
            }
            CentsActionRow(
                label = stringResource(R.string.investments_add),
                onClick = { actions.onAddAsset(account.account.id) },
            )
        }
    }
}

@Composable
private fun AssetRow(
    position: Position,
    showSeparator: Boolean,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = position.holding.name,
        subtitle = "${assetClassLabel(
            position.holding.assetClass,
        )} · ${profitText(position.profit, position.profitRatio)}",
        value = MoneyFormatter.format(position.value),
        showChevron = true,
        showSeparator = showSeparator,
        onClick = onClick,
        modifier = Modifier.testTag(ASSET_ROW_TAG),
    )
}

/** Every sale, newest first, each with its result, and what they made all together. */
private fun LazyListScope.closedOperations(
    account: InvestmentAccount,
    onOpenAsset: (Long) -> Unit,
) {
    val sales = account.sales
    item(key = "closed") {
        CentsListSection(
            header = stringResource(R.string.account_closed_header),
            footer =
                if (sales.isEmpty()) {
                    stringResource(R.string.account_closed_empty)
                } else {
                    stringResource(R.string.account_closed_total, MoneyFormatter.formatSigned(account.realised))
                },
        ) {
            sales.forEachIndexed { index, sale ->
                key(sale.trade.id) {
                    SaleRow(sale, showSeparator = index < sales.lastIndex) { onOpenAsset(sale.holding.id) }
                }
            }
        }
    }
}

@Composable
private fun SaleRow(
    sale: Sale,
    showSeparator: Boolean,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = sale.holding.name,
        subtitle =
            stringResource(
                R.string.account_sale_row,
                sale.trade.date.medium(),
                MoneyFormatter.format(sale.trade.amount),
            ),
        value = profitText(sale.result, sale.ratio),
        valueColor = profitColor(sale.result),
        showSeparator = showSeparator,
        onClick = onClick,
        modifier = Modifier.testTag(SALE_ROW_TAG),
    )
}

internal const val ADD_ASSET_TAG = "addAsset"
internal const val CASH_TAG = "investmentCash"
internal const val INVESTED_TAG = "investmentInvested"
internal const val ASSET_ROW_TAG = "assetRow"
internal const val SALE_ROW_TAG = "saleRow"
internal const val UPDATE_VALUES_TAG = "updateValues"

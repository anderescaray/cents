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
import io.github.anderescaray.cents.domain.model.InvestmentOverview
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.util.MoneyFormatter

/**
 * Invested (`DECISIONS.md` D40): every broker account with its live total, and above them what
 * they come to together — what was put in, what it is worth now, the difference, and what closed
 * operations made — with a chart of the whole through time.
 */
@Composable
fun InvestmentsScreen(
    onBack: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onCreateBrokerAccount: () -> Unit,
    viewModel: InvestmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InvestmentsContent(
        state = state,
        onBack = onBack,
        onOpenAccount = onOpenAccount,
        onCreateBrokerAccount = onCreateBrokerAccount,
        onScaleChange = viewModel::onScaleChange,
    )
}

/** The screen with its state handed to it, so a test can render it without a database. */
@Composable
internal fun InvestmentsContent(
    state: InvestmentsUiState,
    onBack: () -> Unit = {},
    onOpenAccount: (Long) -> Unit = {},
    onCreateBrokerAccount: () -> Unit = {},
    onScaleChange: (ChartScale) -> Unit = {},
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.tab_investments),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.wallet_title), onClick = onBack) },
    ) {
        if (state.loading) return@CentsLargeTitleScaffold
        if (!state.hasAccounts) {
            emptyState(onCreateBrokerAccount)
            return@CentsLargeTitleScaffold
        }
        totals(state.overview)
        chartItem(state.points, state.scale, onScaleChange)
        accounts(state.overview, onOpenAccount)
    }
}

/** What every account comes to, in the currency they share. Nothing when they share none. */
private fun LazyListScope.totals(overview: InvestmentOverview) {
    val sums = overview.sums ?: return
    item(key = "totals") {
        SummarySection(
            lines =
                listOf(
                    SummaryLine(stringResource(R.string.investments_total), sums.total, tag = TOTAL_TAG),
                    SummaryLine(stringResource(R.string.investments_put_in), sums.cost),
                    SummaryLine(stringResource(R.string.investments_worth), sums.invested),
                    profitLine(sums.profit, sums.profitRatio),
                    SummaryLine(
                        stringResource(R.string.investments_realised),
                        sums.realised,
                        isProfit = true,
                        tag = REALISED_TAG,
                    ),
                ),
            footer = if (overview.leavesSomeOut) stringResource(R.string.investments_other_currency) else null,
        )
    }
}

private fun LazyListScope.accounts(
    overview: InvestmentOverview,
    onOpenAccount: (Long) -> Unit,
) {
    item(key = "accounts") {
        CentsListSection(header = stringResource(R.string.investments_accounts_header)) {
            overview.accounts.forEachIndexed { index, item ->
                key(item.account.id) {
                    AccountRow(
                        item,
                        showSeparator = index < overview.accounts.lastIndex,
                    ) { onOpenAccount(item.account.id) }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    item: InvestmentAccount,
    showSeparator: Boolean,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = item.account.name,
        subtitle =
            stringResource(
                R.string.investments_account_subtitle,
                MoneyFormatter.format(item.cash),
                MoneyFormatter.format(item.invested),
            ),
        value = MoneyFormatter.format(item.total),
        showChevron = true,
        showSeparator = showSeparator,
        onClick = onClick,
        modifier = Modifier.testTag(ACCOUNT_ROW_TAG),
    )
}

/** No broker account yet: investments live in one, so that is where the screen sends you. */
private fun LazyListScope.emptyState(onCreateBrokerAccount: () -> Unit) {
    item(key = "empty") {
        CentsListSection {
            CentsListRow(
                title = stringResource(R.string.investments_empty_title),
                subtitle = stringResource(R.string.investments_empty_message),
                showSeparator = true,
            )
            CentsActionRow(label = stringResource(R.string.investments_create_broker), onClick = onCreateBrokerAccount)
        }
    }
}

internal const val TOTAL_TAG = "investmentsTotal"
internal const val ACCOUNT_ROW_TAG = "investmentAccount"

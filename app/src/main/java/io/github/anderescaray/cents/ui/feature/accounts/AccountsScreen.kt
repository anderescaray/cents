package io.github.anderescaray.cents.ui.feature.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwipeableRow
import io.github.anderescaray.cents.ui.components.IconBadge
import io.github.anderescaray.cents.ui.components.MoneyFiguresBlock
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.components.arrive
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter

/**
 * What you have: the figures the owner chose, cash and bank apart from what is invested, then the
 * accounts themselves. Every balance here is computed from the movements that produced it, never stored,
 * so it cannot drift away from the history behind it.
 */
@Composable
fun AccountsScreen(
    onNewAccount: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenInvestments: () -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val removal by viewModel.removal.state.collectAsStateWithLifecycle()

    CentsLargeTitleScaffold(
        title = stringResource(R.string.wallet_title),
        bottomPadding = CentsDimens.sectionSpacing,
        trailing = {
            RoundIconButton(
                iconRes = R.drawable.ic_plus,
                contentDescription = stringResource(R.string.accounts_add),
                onClick = onNewAccount,
                modifier = Modifier.testTag(ADD_ACCOUNT_TAG),
            )
        },
    ) {
        if (state.showEmptyState) {
            emptyState()
        } else {
            state.netWorth?.let { netWorth -> figuresSection(netWorth, state.figures, onOpenInvestments) }
            accountSection(
                key = "active",
                headerRes = R.string.accounts_section_header,
                accounts = state.active,
                footer = { totalFooter(state) },
                onOpenAccount = onOpenAccount,
                onDeleteAccount = viewModel.removal::request,
            )
        }

        if (state.archived.isNotEmpty()) {
            accountSection(
                key = "archived",
                headerRes = R.string.accounts_archived_header,
                accounts = state.archived,
                footer = { stringResource(R.string.accounts_archived_footer) },
                onOpenAccount = onOpenAccount,
                onDeleteAccount = viewModel.removal::request,
            )
        }

        // The Invested tile is the way to the portfolio, and it is not drawn without a net worth
        // (accounts in two currencies), without active accounts, or when the owner switched it
        // off. The portfolio has no tab of its own any more, so it gets a row instead of becoming
        // unreachable.
        if (state.showEmptyState || state.netWorth == null || !state.figures.invested) {
            item(key = "portfolio") {
                CentsListSection {
                    CentsListRow(
                        title = stringResource(R.string.tab_investments),
                        showChevron = true,
                        showSeparator = false,
                        onClick = onOpenInvestments,
                        modifier = Modifier.testTag(PORTFOLIO_ROW_TAG),
                    )
                }
            }
        }
    }

    RemovalDialogs(state = removal, removal = viewModel.removal)
}

/**
 * The money figures the owner chose (`DECISIONS.md` D34): cash and bank, invested, and their
 * total, each on or off in Settings. The Invested tile leads to the portfolio.
 */
private fun LazyListScope.figuresSection(
    netWorth: NetWorth,
    figures: MoneyFigures,
    onOpenInvestments: () -> Unit,
) {
    item(key = "figures") {
        MoneyFiguresBlock(
            netWorth = netWorth,
            figures = figures,
            totalStyle = CentsTheme.typography.display,
            onOpenInvestments = onOpenInvestments,
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin).arrive(0),
        )
    }
}

/**
 * The total, or an explanation instead of one: accounts in different currencies cannot be
 * added up without a conversion rate the app does not have, and inventing one would be a
 * number that lies.
 */
@Composable
private fun totalFooter(state: AccountsUiState): String? {
    val total = state.total
    return when {
        state.active.isEmpty() -> null

        // The figures above say what there is, normal money and invested apart, and adding the
        // brokers into one sum here is what the owner asked not to see (`DECISIONS.md` D34).
        total != null && state.netWorth != null -> null

        total != null -> stringResource(R.string.accounts_total, MoneyFormatter.format(total))

        else -> stringResource(R.string.accounts_total_mixed_currencies)
    }
}

/**
 * Both sections swipe to delete, archived ones included: what "delete an account" means is
 * the same question wherever the account is listed, and an archived account is exactly the
 * one somebody is most likely to want gone for good.
 */
private fun LazyListScope.accountSection(
    key: String,
    @StringRes headerRes: Int,
    accounts: List<AccountWithBalance>,
    footer: @Composable () -> String?,
    onOpenAccount: (Long) -> Unit,
    onDeleteAccount: (Account) -> Unit,
) {
    item(key = "section-$key") {
        val deleteLabel = stringResource(R.string.action_delete)
        CentsListSection(header = stringResource(headerRes), footer = footer()) {
            accounts.forEachIndexed { index, item ->
                val account = item.account
                // Keyed by identity, not by position: a row's swipe offset is remembered
                // against its key, and without one, deleting or reordering hands an open
                // offset to whatever account moves into that slot.
                key(account.id) {
                    CentsSwipeableRow(
                        onDelete = { onDeleteAccount(account) },
                        deleteLabel = deleteLabel,
                    ) { rowModifier ->
                        CentsListRow(
                            modifier = rowModifier,
                            title = account.name,
                            subtitle = stringResource(account.type.labelRes()),
                            value = MoneyFormatter.format(item.balance),
                            valueColor =
                                if (item.balance.isNegative) {
                                    CentsTheme.colors.destructiveText
                                } else {
                                    CentsTheme.colors.label
                                },
                            leading = { IconBadge(iconRes = R.drawable.ic_wallet, color = Color(account.colorArgb)) },
                            showChevron = true,
                            showSeparator = index != accounts.lastIndex,
                            onClick = { onOpenAccount(account.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.emptyState() {
    item(key = "empty") {
        CentsListSection(footer = stringResource(R.string.accounts_empty_footer)) {
            CentsListRow(
                title = stringResource(R.string.accounts_empty_title),
                showSeparator = false,
            )
        }
    }
}

@Composable
internal fun AccountDot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(DOT_SIZE.dp)
                .clip(CircleShape)
                .background(color),
    )
}

private const val DOT_SIZE = 12
internal const val ADD_ACCOUNT_TAG = "addAccount"
internal const val PORTFOLIO_ROW_TAG = "walletPortfolio"

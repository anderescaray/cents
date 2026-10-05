package io.github.anderescaray.cents.ui.feature.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.ui.components.AnimatedMoney
import io.github.anderescaray.cents.ui.components.CentsCard
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwipeableRow
import io.github.anderescaray.cents.ui.components.IconBadge
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.components.arrive
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter

/**
 * What you have: the net worth, what of it is in accounts and what is invested, then the accounts
 * themselves. Every balance here is computed from the movements that produced it, never stored,
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
            state.netWorth?.let { netWorth -> netWorthSection(netWorth, onOpenInvestments) }
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
        // (accounts in two currencies) or without active accounts. The portfolio has no tab of
        // its own any more, so it gets a row instead of becoming unreachable.
        if (state.showEmptyState || state.netWorth == null) {
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
 * What the owner has, in the three numbers they asked for (`DECISIONS.md` D19): the cash in
 * their accounts, what their holdings are worth, and the two together.
 *
 * **Three rows rather than one**, because each answers a different question — what could be
 * spent today, what is tied up, and what it comes to — and a single figure hides which of the
 * two moved when it changes. They only add up at all because a purchase takes its cost out of
 * the account it is held in; before that, the two described the same euros twice.
 *
 * With no holdings the section is two rows and says so by omission, which is the "without the
 * portfolio" half of what was asked for. With holdings but no prices there is nothing to add,
 * so the total stands down rather than reading as zero.
 */
private fun LazyListScope.netWorthSection(
    netWorth: NetWorth,
    onOpenInvestments: () -> Unit,
) {
    item(key = "net-worth") {
        val total = netWorth.total
        Column(
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin).arrive(0),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
        ) {
            CentsCard {
                Text(
                    text = stringResource(R.string.net_worth_total),
                    style = CentsTheme.typography.subhead,
                    color = CentsTheme.colors.secondaryLabel,
                )
                if (total != null) {
                    AnimatedMoney(
                        money = total,
                        style = CentsTheme.typography.display,
                        color = if (total.isNegative) CentsTheme.colors.destructiveText else CentsTheme.colors.label,
                    )
                } else {
                    // Nothing priced is not the same as nothing held, and the two must not
                    // render the same way.
                    Text(
                        text = stringResource(R.string.investments_no_price),
                        style = CentsTheme.typography.title2,
                        color = CentsTheme.colors.secondaryLabel,
                    )
                }
                netWorthFooter(netWorth)?.let { footer ->
                    Text(
                        text = footer,
                        style = CentsTheme.typography.footnote,
                        color = CentsTheme.colors.secondaryLabel,
                    )
                }
            }
            WorthTiles(netWorth, onOpenInvestments)
        }
    }
}

/** What is in accounts and what is invested, side by side, the second leading to the portfolio. */
@Composable
private fun WorthTiles(
    netWorth: NetWorth,
    onOpenInvestments: () -> Unit,
) {
    val invested = netWorth.invested
    Row(horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingM)) {
        WorthTile(
            iconRes = R.drawable.ic_wallet,
            color = CentsTheme.colors.accent,
            label = stringResource(R.string.net_worth_in_accounts),
            value = MoneyFormatter.format(netWorth.inAccounts),
            negative = netWorth.inAccounts.isNegative,
            modifier = Modifier.weight(1f),
        )
        WorthTile(
            iconRes = R.drawable.ic_trend_up,
            color = CentsTheme.colors.positive,
            label = stringResource(R.string.net_worth_invested),
            value = invested?.let { MoneyFormatter.format(it) } ?: stringResource(R.string.investments_no_price),
            onClick = onOpenInvestments,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One half of the net worth, as a small card: the cash, or what the holdings are worth. */
@Composable
private fun WorthTile(
    iconRes: Int,
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    /** Drawn in the warning colour, as an overdrawn total is everywhere else. */
    negative: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    CentsCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(iconRes = iconRes, color = color)
            Spacer(Modifier.weight(1f))
            if (onClick != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = CentsTheme.colors.tertiaryLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(CentsDimens.spacingM))
        Text(text = label, style = CentsTheme.typography.footnote, color = CentsTheme.colors.secondaryLabel)
        Text(
            text = value,
            style = CentsTheme.typography.headline,
            color = if (negative) CentsTheme.colors.destructiveText else CentsTheme.colors.label,
            maxLines = 1,
        )
    }
}

/**
 * What the three numbers leave out, said only when they leave something out.
 *
 * Both cases are money or value that exists and is not in the figure above it, and a net worth
 * that quietly omits either is the kind of number that gets believed and should not be.
 */
@Composable
private fun netWorthFooter(netWorth: NetWorth): String? =
    when {
        netWorth.hasUnpriced -> {
            pluralStringResource(
                R.plurals.net_worth_unpriced,
                netWorth.unpricedCount,
                netWorth.unpricedCount,
            )
        }

        netWorth.hasArchivedMoney -> {
            stringResource(
                R.string.net_worth_archived,
                MoneyFormatter.format(netWorth.inArchivedAccounts),
            )
        }

        else -> {
            null
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

        // Already the *In accounts* figure above, when there is one: said once, not twice.
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

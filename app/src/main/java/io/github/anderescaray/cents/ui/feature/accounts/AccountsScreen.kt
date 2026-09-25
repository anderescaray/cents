package io.github.anderescaray.cents.ui.feature.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwipeableRow
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter

/**
 * Where the money is. Every balance here is computed from the movements that produced it,
 * never stored, so it cannot drift away from the history behind it.
 */
@Composable
fun AccountsScreen(
    onNewAccount: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val removal by viewModel.removal.state.collectAsStateWithLifecycle()

    CentsLargeTitleScaffold(
        title = stringResource(R.string.tab_accounts),
        bottomPadding = CentsDimens.sectionSpacing,
        trailing = {
            val addLabel = stringResource(R.string.accounts_add)
            Box(
                modifier =
                    Modifier
                        .semantics { contentDescription = addLabel }
                        .testTag(ADD_ACCOUNT_TAG)
                        .size(CentsDimens.minRowHeight)
                        .clip(CircleShape)
                        .tappable(onClick = onNewAccount),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.action_add),
                    style = CentsTheme.typography.title2,
                    color = CentsTheme.colors.accent,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        },
    ) {
        if (state.showEmptyState) {
            emptyState()
        } else {
            state.netWorth?.let { netWorth -> netWorthSection(netWorth) }
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
private fun LazyListScope.netWorthSection(netWorth: NetWorth) {
    item(key = "net-worth") {
        val invested = netWorth.invested
        val total = netWorth.total
        val showInvested = invested == null || invested.minorUnits != 0L || netWorth.hasUnpriced
        CentsListSection(
            header = stringResource(R.string.net_worth_header),
            footer = netWorthFooter(netWorth),
        ) {
            CentsListRow(
                title = stringResource(R.string.net_worth_in_accounts),
                value = MoneyFormatter.format(netWorth.inAccounts),
                valueColor =
                    if (netWorth.inAccounts.isNegative) {
                        CentsTheme.colors.destructive
                    } else {
                        CentsTheme.colors.label
                    },
                showSeparator = showInvested,
            )
            if (showInvested) {
                CentsListRow(
                    title = stringResource(R.string.net_worth_invested),
                    // Nothing priced is not the same as nothing held, and the two must not
                    // render the same way.
                    value =
                        invested?.let { MoneyFormatter.format(it) }
                            ?: stringResource(R.string.investments_no_price),
                    valueColor =
                        if (invested == null) CentsTheme.colors.secondaryLabel else CentsTheme.colors.label,
                    showSeparator = total != null,
                )
            }
            if (total != null && showInvested) {
                CentsListRow(
                    title = stringResource(R.string.net_worth_total),
                    value = MoneyFormatter.format(total),
                    valueColor =
                        if (total.isNegative) CentsTheme.colors.destructive else CentsTheme.colors.label,
                    showSeparator = false,
                )
            }
        }
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
                                    CentsTheme.colors.destructive
                                } else {
                                    CentsTheme.colors.label
                                },
                            leading = { AccountDot(Color(account.colorArgb)) },
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

package io.github.anderescaray.cents.ui.feature.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwipeableRow
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
                        .clickable(onClick = onNewAccount),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.action_add),
                    style = CentsTheme.typography.title2,
                    color = CentsTheme.colors.accent,
                )
            }
        },
    ) {
        if (state.showEmptyState) {
            emptyState()
        } else {
            accountSection(
                key = "active",
                headerRes = R.string.accounts_section_header,
                accounts = state.active,
                footer = { totalFooter(state) },
                onOpenAccount = onOpenAccount,
                onDeleteAccount = viewModel::requestRemoval,
            )
        }

        if (state.archived.isNotEmpty()) {
            accountSection(
                key = "archived",
                headerRes = R.string.accounts_archived_header,
                accounts = state.archived,
                footer = { stringResource(R.string.accounts_archived_footer) },
                onOpenAccount = onOpenAccount,
                onDeleteAccount = viewModel::requestRemoval,
            )
        }
    }

    RemovalDialogs(state = removal, removal = viewModel.removal)
}

/**
 * The total, or an explanation instead of one: accounts in different currencies cannot be
 * added up without a conversion rate the app does not have, and inventing one would be a
 * number that lies.
 *
 * A removal that was refused takes the line instead. It is rare and it is transient, but a
 * swipe that ends with the row still there has to account for itself somewhere.
 */
@Composable
private fun totalFooter(state: AccountsUiState): String? {
    val total = state.total
    return when {
        state.removalFailed -> stringResource(R.string.delete_failed)
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

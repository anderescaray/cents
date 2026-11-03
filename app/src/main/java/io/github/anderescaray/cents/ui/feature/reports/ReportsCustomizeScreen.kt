package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwitch
import io.github.anderescaray.cents.ui.components.ReorderState
import io.github.anderescaray.cents.ui.components.reorderHandle
import io.github.anderescaray.cents.ui.components.reorderableRow
import io.github.anderescaray.cents.ui.feature.filters.displayName
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * Reports › Customize: the settings of the whole tab, kept off it so the charts start at the top.
 *
 * **Which account** the tab is about, when there is more than one to choose from. **The order of
 * the charts**, folded into one row until it is opened: every chart, with a switch to show or hide
 * it and a handle to drag it into place. TalkBack, which cannot drag, gets *move up* and *move
 * down* as actions on each row.
 *
 * The account is the Reports tab's own selection, read from its view model, so choosing one here
 * is what the tab shows on the way back.
 */
@Composable
fun ReportsCustomizeScreen(
    onBack: () -> Unit,
    reportsViewModel: ReportsViewModel,
    viewModel: ReportLayoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reports by reportsViewModel.uiState.collectAsStateWithLifecycle()
    ReportsCustomizeContent(
        state = state,
        onBack = onBack,
        onMove = viewModel::move,
        onSetVisible = viewModel::setVisible,
        onReset = viewModel::reset,
        accounts = if (reports.canPickAccount) reports.accounts else emptyList(),
        accountId = reports.accountId,
        onSelectAccount = reportsViewModel::selectAccount,
    )
}

@Composable
internal fun ReportsCustomizeContent(
    state: ReportLayoutUiState,
    onBack: () -> Unit,
    onMove: (ReportCard, Int) -> Unit,
    onSetVisible: (ReportCard, Boolean) -> Unit,
    onReset: () -> Unit,
    accounts: List<Account> = emptyList(),
    accountId: Long? = null,
    onSelectAccount: (Long?) -> Unit = {},
) {
    var orderOpen by rememberSaveable { mutableStateOf(false) }
    CentsLargeTitleScaffold(
        title = stringResource(R.string.reports_customize_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.tab_reports), onClick = onBack) },
    ) {
        if (accounts.isNotEmpty()) {
            item(key = "account") {
                AccountChoice(accounts = accounts, accountId = accountId, onSelect = onSelectAccount)
            }
        }
        item(key = "cards") {
            ChartOrder(
                choices = state.choices,
                open = orderOpen,
                onToggle = { orderOpen = !orderOpen },
                onMove = onMove,
                onSetVisible = onSetVisible,
            )
        }
        item(key = "reset") {
            CentsListSection {
                CentsListRow(
                    title = stringResource(R.string.reports_customize_reset),
                    showSeparator = false,
                    onClick = onReset,
                )
            }
        }
    }
}

/** All accounts, or one: the charts are about the account ticked here. */
@Composable
private fun AccountChoice(
    accounts: List<Account>,
    accountId: Long?,
    onSelect: (Long?) -> Unit,
) {
    CentsListSection(header = stringResource(R.string.reports_account_header)) {
        CentsListRow(
            title = stringResource(R.string.reports_all_accounts),
            selected = accountId == null,
            onClick = { onSelect(null) },
        )
        accounts.forEachIndexed { index, account ->
            CentsListRow(
                title = account.displayName(),
                selected = accountId == account.id,
                showSeparator = index != accounts.lastIndex,
                onClick = { onSelect(account.id) },
            )
        }
    }
}

/**
 * *Order of charts* as one row saying how many are shown; opened, every chart under it with its
 * handle and its switch.
 */
@Composable
private fun ChartOrder(
    choices: List<ReportCardChoice>,
    open: Boolean,
    onToggle: () -> Unit,
    onMove: (ReportCard, Int) -> Unit,
    onSetVisible: (ReportCard, Boolean) -> Unit,
) {
    val turn by animateFloatAsState(if (open) OPEN_TURN else 0f, label = "orderChevron")
    CentsListSection(footer = stringResource(R.string.reports_customize_footer).takeIf { open }) {
        CentsListRow(
            title = stringResource(R.string.reports_customize_order),
            subtitle =
                stringResource(
                    R.string.reports_customize_order_summary,
                    choices.count { it.visible },
                    choices.size,
                ),
            trailing = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = CentsTheme.colors.tertiaryLabel,
                    modifier = Modifier.size(CHEVRON.dp).rotate(turn),
                )
            },
            showSeparator = open,
            onClick = onToggle,
        )
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val reorder = remember { ReorderState() }
            Column {
                choices.forEachIndexed { index, choice ->
                    androidx.compose.runtime.key(choice.card) {
                        ChartRow(
                            choice = choice,
                            index = index,
                            count = choices.size,
                            reorder = reorder,
                            onMove = { from, to -> onMove(choices[from].card, to - from) },
                            onSetVisible = onSetVisible,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartRow(
    choice: ReportCardChoice,
    index: Int,
    count: Int,
    reorder: ReorderState,
    onMove: (from: Int, to: Int) -> Unit,
    onSetVisible: (ReportCard, Boolean) -> Unit,
) {
    val label = stringResource(choice.card.labelRes)
    val up = stringResource(R.string.reports_customize_up, label)
    val down = stringResource(R.string.reports_customize_down, label)
    Box(
        modifier =
            Modifier
                .reorderableRow(reorder, index, count)
                .semantics {
                    customActions =
                        buildList {
                            if (index > 0) {
                                add(
                                    CustomAccessibilityAction(up) {
                                        onMove(index, index - 1)
                                        true
                                    },
                                )
                            }
                            if (index < count - 1) {
                                add(
                                    CustomAccessibilityAction(down) {
                                        onMove(index, index + 1)
                                        true
                                    },
                                )
                            }
                        }
                },
    ) {
        CentsListRow(
            title = label,
            leading = {
                Icon(
                    painter = painterResource(R.drawable.ic_reorder_handle),
                    contentDescription = stringResource(R.string.reports_customize_reorder, label),
                    tint = CentsTheme.colors.tertiaryLabel,
                    modifier = Modifier.reorderHandle(reorder, index, count, onMove).size(HANDLE.dp),
                )
            },
            trailing = {
                CentsSwitch(
                    checked = choice.visible,
                    onCheckedChange = { onSetVisible(choice.card, it) },
                )
            },
            showSeparator = index != count - 1,
        )
    }
}

private const val CHEVRON = 16
private const val HANDLE = 24
private const val OPEN_TURN = 90f

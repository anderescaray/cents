package io.github.anderescaray.cents.ui.feature.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.BalanceAdjustment
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.NOTE_MAX_LINES
import io.github.anderescaray.cents.ui.components.UndoBanner
import io.github.anderescaray.cents.ui.components.movementTitle
import io.github.anderescaray.cents.ui.components.movementValueColor
import io.github.anderescaray.cents.ui.components.open
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.undo.DeletedTransaction
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.LocalDate

/**
 * One account: its balance, what it did this month, and every movement behind that number.
 * The balance is the opening amount plus these rows and nothing else — movements, and the
 * balance corrections dated among them — which is why they sit on the same screen.
 */
@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenTransfer: (Long) -> Unit,
    onOpenTrade: (Long, Long) -> Unit,
    onSetBalance: (Long) -> Unit = {},
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isGone) {
        if (state.isGone) onBack()
    }

    AccountDetailContent(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onOpenTransaction = onOpenTransaction,
        onOpenTransfer = onOpenTransfer,
        onOpenTrade = onOpenTrade,
        onUndo = viewModel::undoDeletion,
        onSetBalance = onSetBalance,
        onRemoveCorrection = viewModel::removeCorrection,
    )
}

/**
 * The screen itself, without the view model: what [AccountDetailScreen] draws, and what a test
 * can draw from a state it builds.
 */
@Composable
internal fun AccountDetailContent(
    state: AccountDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenTransfer: (Long) -> Unit,
    onOpenTrade: (Long, Long) -> Unit,
    onUndo: () -> Unit,
    onSetBalance: (Long) -> Unit = {},
    onRemoveCorrection: (Long) -> Unit = {},
) {
    val account = state.account
    // The correction a tap asked to remove, waiting on the confirmation every removal gets (rule 7).
    var removing by remember { mutableStateOf<BalanceAdjustment?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        CentsLargeTitleScaffold(
            title = account?.account?.name ?: stringResource(R.string.tab_accounts),
            bottomPadding = CentsDimens.sectionSpacing,
            leading = {
                CentsBackButton(label = stringResource(R.string.tab_accounts), onClick = onBack)
            },
            trailing = {
                if (account != null) {
                    Text(
                        text = stringResource(R.string.action_edit),
                        style = CentsTheme.typography.body,
                        color = CentsTheme.colors.accent,
                        modifier =
                            Modifier
                                .tappable(onClick = { onEdit(account.account.id) })
                                .padding(CentsDimens.spacingXs),
                    )
                }
            },
        ) {
            if (account != null) {
                balanceHeader(account, onSetBalance = { onSetBalance(account.account.id) })
                state.monthSummary?.let { summary -> monthSummary(summary) }
            }

            if (state.isEmpty) {
                emptyState()
            }

            state.days.forEach { day ->
                movementDay(day, state.today, onOpenTransaction, onOpenTransfer, onOpenTrade) { removing = it }
            }
        }

        state.undoableDeletion?.let { deletion ->
            UndoBanner(
                isTransfer = deletion is DeletedTransaction.Transfer,
                onUndo = onUndo,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(CentsDimens.screenMargin),
            )
        }
    }

    val correction = removing
    if (correction != null && account != null) {
        RemoveCorrectionAlert(
            balance = account.balance,
            correction = correction,
            onConfirm = {
                onRemoveCorrection(correction.id)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

/** Says what the balance becomes without the correction, before it goes. */
@Composable
private fun RemoveCorrectionAlert(
    balance: Money,
    correction: BalanceAdjustment,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CentsAlert(
        title = stringResource(R.string.balance_correction_remove_title),
        message =
            stringResource(
                R.string.balance_correction_remove_message,
                MoneyFormatter.format(balance - correction.amount),
            ),
        confirmLabel = stringResource(R.string.balance_correction_remove_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.action_cancel),
        destructive = true,
    )
}

private fun LazyListScope.balanceHeader(
    account: AccountWithBalance,
    onSetBalance: () -> Unit,
) {
    item(key = "balance") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = MoneyFormatter.format(account.balance),
                style = CentsTheme.typography.largeTitle,
                color =
                    if (account.balance.isNegative) {
                        CentsTheme.colors.destructive
                    } else {
                        CentsTheme.colors.label
                    },
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(account.account.type.labelRes()),
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
            )
            // Not the opening balance, which is edited in the account's form: this is what the
            // account holds today, for when movements were missed.
            Text(
                text = stringResource(R.string.balance_set_action),
                style = CentsTheme.typography.subhead,
                color = CentsTheme.colors.accent,
                modifier =
                    Modifier
                        .testTag(SET_BALANCE_TAG)
                        .tappable(onClick = onSetBalance)
                        .padding(CentsDimens.spacingXs),
            )
        }
    }
}

private fun LazyListScope.monthSummary(summary: PeriodSummary) {
    item(key = "summary") {
        CentsListSection(header = stringResource(R.string.transactions_this_month)) {
            CentsListRow(
                title = stringResource(R.string.summary_income),
                value = MoneyFormatter.format(summary.income),
                valueColor = CentsTheme.colors.positive,
            )
            CentsListRow(
                title = stringResource(R.string.summary_expenses),
                value = MoneyFormatter.format(summary.expenses),
                valueColor = CentsTheme.colors.destructive,
                showSeparator = false,
            )
        }
    }
}

private fun LazyListScope.emptyState() {
    item(key = "empty") {
        CentsListSection(footer = stringResource(R.string.account_empty_footer)) {
            CentsListRow(
                title = stringResource(R.string.account_empty_title),
                showSeparator = false,
            )
        }
    }
}

private fun LazyListScope.movementDay(
    day: AccountDay,
    today: LocalDate,
    onOpenTransaction: (Long) -> Unit,
    onOpenTransfer: (Long) -> Unit,
    onOpenTrade: (Long, Long) -> Unit,
    onOpenCorrection: (BalanceAdjustment) -> Unit,
) {
    item(key = "day-${day.date}") {
        CentsListSection(header = day.date.relativeLabel(today)) {
            day.items.forEachIndexed { index, details ->
                val movement = details.transaction
                CentsListRow(
                    title = details.movementTitle(),
                    subtitle = movement.note?.takeIf { it.isNotBlank() },
                    subtitleMaxLines = NOTE_MAX_LINES,
                    value = MoneyFormatter.formatSigned(movement.signedAmount),
                    valueColor = details.movementValueColor(),
                    showSeparator = index != day.items.lastIndex || day.corrections.isNotEmpty(),
                    onClick = {
                        details.open(onOpenTransaction, onOpenTransfer, onOpenTrade)
                    },
                )
            }
            day.corrections.forEachIndexed { index, correction ->
                // Grey, like a transfer leg: it moves the balance and is neither earned nor spent.
                CentsListRow(
                    title = stringResource(R.string.balance_correction_row),
                    value = MoneyFormatter.formatSigned(correction.amount),
                    valueColor = CentsTheme.colors.secondaryLabel,
                    showSeparator = index != day.corrections.lastIndex,
                    onClick = { onOpenCorrection(correction) },
                    modifier = Modifier.testTag(CORRECTION_ROW_TAG),
                )
            }
        }
    }
}

internal const val SET_BALANCE_TAG = "accountSetBalance"
internal const val CORRECTION_ROW_TAG = "accountCorrection"

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.IconBadge
import io.github.anderescaray.cents.ui.components.MovementBadge
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
        corrections =
            CorrectionActions(
                onAsk = viewModel::askToRemoveCorrection,
                onConfirm = viewModel::confirmCorrectionRemoval,
                onCancel = viewModel::cancelCorrectionRemoval,
                onUndo = viewModel::undoCorrectionRemoval,
                onFailureSeen = viewModel::dismissCorrectionFailure,
            ),
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
    corrections: CorrectionActions = CorrectionActions(),
) {
    val account = state.account

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
                        color = CentsTheme.colors.accentText,
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
                movementDay(day, state.today, onOpenTransaction, onOpenTransfer, onOpenTrade, corrections.onAsk)
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
        if (state.undoableCorrection != null) {
            UndoBanner(
                message = stringResource(R.string.balance_correction_removed),
                onUndo = corrections.onUndo,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(CentsDimens.screenMargin),
            )
        }
    }

    CorrectionAlerts(state, corrections)
}

/** The question before a correction goes, and the answer when the database refused. */
@Composable
private fun CorrectionAlerts(
    state: AccountDetailUiState,
    corrections: CorrectionActions,
) {
    state.removal?.let { removal ->
        RemoveCorrectionAlert(
            balanceAfter = removal.balanceAfter,
            onConfirm = corrections.onConfirm,
            onDismiss = corrections.onCancel,
        )
    }

    if (state.corrections.failed) {
        CentsAlert(
            title = stringResource(R.string.balance_correction_failed_title),
            message = stringResource(R.string.balance_correction_failed_message),
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = corrections.onFailureSeen,
            onDismiss = corrections.onFailureSeen,
        )
    }
}

/** What can be done to a balance correction from this screen. */
internal data class CorrectionActions(
    val onAsk: (Long) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onFailureSeen: () -> Unit = {},
)

/** Says what the balance becomes without the correction, before it goes. */
@Composable
private fun RemoveCorrectionAlert(
    balanceAfter: Money,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CentsAlert(
        title = stringResource(R.string.balance_correction_remove_title),
        message = stringResource(R.string.balance_correction_remove_message, MoneyFormatter.format(balanceAfter)),
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
                        CentsTheme.colors.destructiveText
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
                color = CentsTheme.colors.accentText,
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
                valueColor = CentsTheme.colors.positiveText,
            )
            CentsListRow(
                title = stringResource(R.string.summary_expenses),
                value = MoneyFormatter.format(summary.expenses),
                valueColor = CentsTheme.colors.destructiveText,
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
    onOpenCorrection: (Long) -> Unit,
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
                    leading = { details.MovementBadge() },
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
                    // A badge like every movement beside it, so the titles line up.
                    leading = { IconBadge(iconRes = R.drawable.ic_wallet, color = CentsTheme.colors.secondaryLabel) },
                    showSeparator = index != day.corrections.lastIndex,
                    onClick = { onOpenCorrection(correction.id) },
                    modifier = Modifier.testTag(CORRECTION_ROW_TAG),
                )
            }
        }
    }
}

internal const val SET_BALANCE_TAG = "accountSetBalance"
internal const val CORRECTION_ROW_TAG = "accountCorrection"

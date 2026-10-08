package io.github.anderescaray.cents.ui.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.BalanceChange
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Telling the app what an account really holds, when movements were missed, without inventing an
 * income or an expense to explain the difference.
 *
 * Everything that decides the outcome is on the screen before Save: the balance Cents has, the
 * one typed, the difference between them, and what happens to the earlier balances — which the
 * owner picks every time, because the two answers draw different histories.
 */
@Composable
fun AccountBalanceScreen(
    onDone: () -> Unit,
    viewModel: AccountBalanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Saved, or the account was deleted while this screen sat over it. Keyed on the one answer
    // rather than on the account, whose balance can still change while the screen leaves: a
    // second run would pop the screen underneath this one too.
    val leave = state.form.finished || (!state.isLoading && state.account == null)
    LaunchedEffect(leave) {
        if (leave) onDone()
    }

    AccountBalanceContent(
        state = state,
        actions =
            AccountBalanceActions(
                onCancel = onDone,
                onSave = viewModel::save,
                onDigit = viewModel::appendDigit,
                onDecimalSeparator = viewModel::appendDecimalSeparator,
                onDelete = viewModel::deleteDigit,
                onToggleSign = viewModel::toggleSign,
                onChoose = viewModel::choose,
            ),
    )
}

internal data class AccountBalanceActions(
    val onCancel: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDigit: (Char) -> Unit = {},
    val onDecimalSeparator: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onToggleSign: () -> Unit = {},
    val onChoose: (BalanceChange) -> Unit = {},
)

/** The screen without the view model, so a test can draw it from a state it builds. */
@Composable
internal fun AccountBalanceContent(
    state: AccountBalanceUiState,
    actions: AccountBalanceActions,
) {
    // Up on arrival, because typing the balance is why the screen was opened; down once a choice
    // is made, so the summary it changes is not hidden under the keys. Tapping the amount brings
    // it back. Kept here rather than in the shared keypad state, which belongs to the movement
    // forms and is about to change for them.
    var keypadShown by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        CentsModalBar(
            title = stringResource(R.string.balance_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = actions.onCancel,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = actions.onSave,
            confirmEnabled = state.canSave,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
        ) {
            item(key = "target") {
                TargetHeader(
                    state = state,
                    onAmountTapped = { keypadShown = true },
                    onToggleSign = actions.onToggleSign,
                )
            }
            comparison(state)
            modes(
                state = state,
                onChoose = { change ->
                    keypadShown = false
                    actions.onChoose(change)
                },
            )
        }

        if (keypadShown) {
            CentsKeypad(
                onDigit = actions.onDigit,
                onDecimalSeparator = actions.onDecimalSeparator,
                onDelete = actions.onDelete,
            )
        }
    }
}

/** The balance being typed, and its sign. */
@Composable
private fun TargetHeader(
    state: AccountBalanceUiState,
    onAmountTapped: () -> Unit,
    onToggleSign: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = CentsDimens.spacingL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingXs),
    ) {
        Text(
            // Blank for the moment the account takes to load, rather than a guessed currency.
            text = state.currency?.let { AmountEntry.format(state.form.input, it, state.form.negative) }.orEmpty(),
            style = CentsTheme.typography.largeTitle,
            color =
                when {
                    !state.hasTarget -> CentsTheme.colors.secondaryLabel
                    state.targetIsNegative -> CentsTheme.colors.destructiveText
                    else -> CentsTheme.colors.label
                },
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .testTag(BALANCE_TARGET_TAG)
                    .tappable(onClick = onAmountTapped)
                    .padding(CentsDimens.spacingXs),
        )
        Text(
            text = stringResource(R.string.balance_new_label),
            style = CentsTheme.typography.footnote,
            color = CentsTheme.colors.secondaryLabel,
        )
        Text(
            text = stringResource(if (state.form.negative) R.string.account_owed else R.string.account_held),
            style = CentsTheme.typography.subhead,
            color = CentsTheme.colors.accentText,
            modifier =
                Modifier
                    .testTag(BALANCE_SIGN_TAG)
                    .tappable(onClick = onToggleSign)
                    .padding(CentsDimens.spacingXs),
        )
    }
}

/** What Cents has now, and how far the typed balance is from it. */
private fun LazyListScope.comparison(state: AccountBalanceUiState) {
    val current = state.current ?: return
    item(key = "comparison") {
        val difference = state.difference
        CentsListSection(
            footer =
                when {
                    state.form.writeFailed -> stringResource(R.string.balance_write_failed)
                    state.alreadyThere -> stringResource(R.string.balance_already_there)
                    else -> null
                },
        ) {
            CentsListRow(
                title = stringResource(R.string.balance_current),
                value = MoneyFormatter.format(current),
                valueColor = if (current.isNegative) CentsTheme.colors.destructiveText else CentsTheme.colors.label,
                showSeparator = difference != null,
            )
            if (difference != null) {
                CentsListRow(
                    title = stringResource(R.string.balance_difference),
                    value = MoneyFormatter.formatSigned(difference),
                    valueColor =
                        when {
                            difference.isZero -> CentsTheme.colors.secondaryLabel
                            difference.isNegative -> CentsTheme.colors.destructiveText
                            else -> CentsTheme.colors.positiveText
                        },
                    showSeparator = false,
                    modifier = Modifier.testTag(BALANCE_DIFFERENCE_TAG),
                )
            }
        }
    }
}

/**
 * The two ways the earlier balances can go, each saying what it does with the numbers on screen.
 * Neither is ticked until the owner picks one.
 */
private fun LazyListScope.modes(
    state: AccountBalanceUiState,
    onChoose: (BalanceChange) -> Unit,
) {
    item(key = "modes") {
        val difference = state.pendingDifference
        val rebased = state.rebasedOpening
        val opening = state.opening
        val day = state.today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        CentsListSection(
            header = stringResource(R.string.balance_mode_header),
            footer = stringResource(R.string.balance_mode_footer),
        ) {
            CentsListRow(
                title = stringResource(R.string.balance_mode_correct_title),
                subtitle =
                    if (difference != null) {
                        stringResource(
                            R.string.balance_mode_correct_detail,
                            MoneyFormatter.formatSigned(difference),
                            day,
                        )
                    } else {
                        stringResource(R.string.balance_mode_correct_generic)
                    },
                selected = state.form.change == BalanceChange.CORRECT_FROM_TODAY,
                onClick = { onChoose(BalanceChange.CORRECT_FROM_TODAY) },
                modifier = Modifier.testTag(BALANCE_CORRECT_TAG),
            )
            CentsListRow(
                title = stringResource(R.string.balance_mode_rebase_title),
                subtitle =
                    if (difference != null && rebased != null && opening != null) {
                        stringResource(
                            R.string.balance_mode_rebase_detail,
                            MoneyFormatter.format(opening),
                            MoneyFormatter.format(rebased),
                            MoneyFormatter.formatSigned(difference),
                        )
                    } else {
                        stringResource(R.string.balance_mode_rebase_generic)
                    },
                selected = state.form.change == BalanceChange.REBASE_OPENING,
                showSeparator = false,
                onClick = { onChoose(BalanceChange.REBASE_OPENING) },
                modifier = Modifier.testTag(BALANCE_REBASE_TAG),
            )
        }
    }
}

internal const val BALANCE_TARGET_TAG = "balanceTarget"
internal const val BALANCE_SIGN_TAG = "balanceSign"
internal const val BALANCE_DIFFERENCE_TAG = "balanceDifference"
internal const val BALANCE_CORRECT_TAG = "balanceCorrect"
internal const val BALANCE_REBASE_TAG = "balanceRebase"

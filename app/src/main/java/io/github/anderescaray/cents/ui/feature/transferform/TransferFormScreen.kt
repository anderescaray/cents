package io.github.anderescaray.cents.ui.feature.transferform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.ui.components.AmountKeypadState
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsButton
import io.github.anderescaray.cents.ui.components.CentsChip
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.components.EntryKind
import io.github.anderescaray.cents.ui.components.EntryKindControl
import io.github.anderescaray.cents.ui.components.rememberAmountKeypadState
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.feature.transactionform.FormTopBar
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel

/**
 * Moving money between two of the owner's own accounts.
 *
 * It looks like the movement form on purpose — the same bar, the same big amount, the same
 * keypad and the same one button — because it is the same act of recording what happened to some
 * money. What differs is what the form asks: two accounts instead of a category, as two rows of
 * chips, and a fee that most transfers leave empty.
 */
@Composable
fun TransferFormScreen(
    onDone: () -> Unit,
    onSwitchKind: (EntryKind, String) -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: TransferFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keypad = rememberAmountKeypadState(shownInitially = viewModel.isNewRecord)
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedCard)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .imePadding(),
    ) {
        FormTopBar(
            title = stringResource(if (state.isEditing) R.string.edit_transfer_title else R.string.add_transfer_title),
            onCancel = onDone,
            onDelete = viewModel::requestDeletion.takeIf { state.isEditing },
        )

        FormFields(
            state = state,
            viewModel = viewModel,
            keypad = keypad,
            onSwitchKind = { kind -> onSwitchKind(kind, state.amountInput) },
            onAmountTapped = { field ->
                // Clearing focus is what lowers the system keyboard; without it the two
                // would be stacked on each other.
                focusManager.clearFocus()
                viewModel.typeInto(field)
                keypad.showForAmount()
            },
            onCreateAccount = onCreateAccount,
            modifier = Modifier.weight(1f),
        )

        if (keypad.isShown) {
            CentsKeypad(
                onDigit = viewModel::appendDigit,
                onDecimalSeparator = viewModel::appendDecimalSeparator,
                onDelete = viewModel::deleteDigit,
                applyBottomInset = false,
            )
        }
        CentsButton(
            text = stringResource(if (state.isEditing) R.string.action_save else R.string.transfer_confirm),
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier =
                Modifier
                    .padding(horizontal = CentsDimens.screenMargin)
                    .padding(
                        top = if (keypad.isShown) CentsDimens.spacingXs else CentsDimens.spacingS,
                        bottom =
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                CentsDimens.spacingM,
                    ),
        )
    }

    FormDialogs(state = state, viewModel = viewModel)
}

/** What the form puts on top of itself: the calendar, and the deletion it asks about. */
@Composable
private fun FormDialogs(
    state: TransferFormUiState,
    viewModel: TransferFormViewModel,
) {
    if (state.pickingDate) {
        CentsDatePicker(
            selected = state.date,
            today = state.today,
            onSelect = viewModel::setDate,
            onDismiss = viewModel::dismissDatePicker,
        )
    }

    if (state.confirmingDeletion) {
        CentsAlert(
            title = stringResource(R.string.transfer_delete_title),
            message = stringResource(R.string.transfer_delete_undo_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::confirmDeletion,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::cancelDeletion,
            destructive = true,
        )
    }
}

@Composable
private fun FormFields(
    state: TransferFormUiState,
    viewModel: TransferFormViewModel,
    keypad: AmountKeypadState,
    onSwitchKind: (EntryKind) -> Unit,
    onAmountTapped: (TransferField) -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        if (state.canChangeKind) {
            EntryKindControl(
                selected = EntryKind.TRANSFER,
                onSelect = { kind -> if (kind != EntryKind.TRANSFER) onSwitchKind(kind) },
                modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
            )
        }

        AmountHeader(state, onClick = { onAmountTapped(TransferField.AMOUNT) })

        if (state.writeFailed) {
            Text(
                text = stringResource(R.string.transaction_write_failed),
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.destructiveText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CentsDimens.screenMargin),
            )
        }

        Accounts(state = state, viewModel = viewModel, onCreateAccount = onCreateAccount)

        Details(
            state = state,
            viewModel = viewModel,
            keypad = keypad,
            onFeeTapped = { onAmountTapped(TransferField.FEE) },
        )
    }
}

/**
 * Where the money leaves and where it lands, as two rows of chips, with **Swap them** between:
 * one tap for the mistake this form makes easiest to make.
 */
@Composable
private fun Accounts(
    state: TransferFormUiState,
    viewModel: TransferFormViewModel,
    onCreateAccount: () -> Unit,
) {
    if (state.needsSecondAccount) {
        CentsListSection(
            header = stringResource(R.string.transfer_from),
            footer = stringResource(R.string.transfer_needs_two_accounts_footer),
        ) {
            CentsListRow(
                title = stringResource(R.string.transfer_needs_two_accounts),
                showChevron = true,
                showSeparator = false,
                onClick = onCreateAccount,
            )
        }
        return
    }
    AccountChips(
        label = stringResource(R.string.transfer_from),
        accounts = state.accounts,
        selectedId = state.fromAccountId,
        onSelect = viewModel::selectFrom,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CentsDimens.screenMargin),
        horizontalArrangement = Arrangement.End,
    ) {
        CentsChip(
            label = stringResource(R.string.transfer_swap),
            iconRes = R.drawable.ic_transfer,
            onClick = viewModel::swapAccounts,
            modifier = Modifier.testTag(SWAP_TAG),
        )
    }
    AccountChips(
        label = stringResource(R.string.transfer_to),
        accounts = state.destinations,
        selectedId = state.toAccountId,
        onSelect = viewModel::selectTo,
    )
}

@Composable
private fun AccountChips(
    label: String,
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingS)) {
        Text(
            text = label.uppercase(),
            style = CentsTheme.typography.overline,
            color = CentsTheme.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin + CentsDimens.spacingXs),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = CentsDimens.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
        ) {
            items(accounts, key = { it.id }) { account ->
                CentsChip(
                    label = account.name,
                    colorArgb = account.colorArgb,
                    showBadge = false,
                    iconRes = R.drawable.ic_wallet,
                    selected = account.id == selectedId,
                    onClick = { onSelect(account.id) },
                )
            }
        }
    }
}

/** When it happened, what it cost, and what it was for. */
@Composable
private fun Details(
    state: TransferFormUiState,
    viewModel: TransferFormViewModel,
    keypad: AmountKeypadState,
    onFeeTapped: () -> Unit,
) {
    // With a fee typed, the footer stops explaining fees and says what this one does: the amount
    // above is what *arrives*, and the source account is short by more than that. It is the one
    // number on this form that is nowhere on it.
    val footer =
        if (state.fee.minorUnits == 0L) {
            stringResource(R.string.transfer_fee_footer)
        } else {
            stringResource(R.string.transfer_leaving_footer, MoneyFormatter.format(state.amountLeaving))
        }
    CentsListSection(footer = footer) {
        CentsListRow(
            title = stringResource(R.string.field_date),
            value = state.date.relativeLabel(state.today),
            showChevron = true,
            onClick = viewModel::openDatePicker,
        )
        CentsListRow(
            title = stringResource(R.string.field_fee),
            value = AmountEntry.format(state.feeInput, state.currency),
            valueColor =
                if (state.typing == TransferField.FEE) {
                    CentsTheme.colors.accentText
                } else {
                    CentsTheme.colors.secondaryLabel
                },
            onClick = onFeeTapped,
            modifier = Modifier.testTag(FEE_ROW_TAG),
        )
        CentsTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            placeholder = stringResource(R.string.field_note_placeholder),
            modifier =
                Modifier
                    .testTag(NOTE_FIELD_TAG)
                    .onFocusChanged { keypad.onTextFieldFocus(it.isFocused) },
        )
    }
}

@Composable
private fun AmountHeader(
    state: TransferFormUiState,
    onClick: () -> Unit,
) {
    Text(
        text = AmountEntry.format(state.amountInput, state.currency),
        style = CentsTheme.typography.display.copy(fontSize = AMOUNT_SIZE.sp, lineHeight = (AMOUNT_SIZE + 8).sp),
        color =
            if (state.typing == TransferField.AMOUNT) {
                CentsTheme.colors.label
            } else {
                CentsTheme.colors.secondaryLabel
            },
        textAlign = TextAlign.Center,
        maxLines = 1,
        // Shrinks rather than clips: 12.345,67 € on a narrow phone, or a large system font, must
        // still show every digit of what is about to be saved.
        autoSize = TextAutoSize.StepBased(minFontSize = AMOUNT_MIN_SIZE.sp, maxFontSize = AMOUNT_SIZE.sp),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(AMOUNT_HEADER_TAG)
                .tappable(onClick = onClick)
                .padding(horizontal = CentsDimens.screenMargin),
    )
}

private const val AMOUNT_SIZE = 52
private const val AMOUNT_MIN_SIZE = 20

internal const val NOTE_FIELD_TAG = "transferNote"
internal const val AMOUNT_HEADER_TAG = "transferAmount"
internal const val FEE_ROW_TAG = "transferFee"
internal const val SWAP_TAG = "transferSwap"

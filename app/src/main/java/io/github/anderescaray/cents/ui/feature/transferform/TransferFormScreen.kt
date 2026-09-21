package io.github.anderescaray.cents.ui.feature.transferform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.AmountKeypadState
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.components.EntryKind
import io.github.anderescaray.cents.ui.components.EntryKindControl
import io.github.anderescaray.cents.ui.components.rememberAmountKeypadState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel

/**
 * Moving money between two of the owner's own accounts.
 *
 * It looks like the movement form on purpose — the same bar, the same big amount, the same
 * keypad — because it is the same act of recording what happened to some money. What differs
 * is what the form asks: two accounts instead of one, no category, and a fee that most
 * transfers leave empty.
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
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .imePadding(),
    ) {
        CentsModalBar(
            title =
                stringResource(
                    if (state.isEditing) R.string.edit_transfer_title else R.string.add_transfer_title,
                ),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
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
            )
        }
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
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        item(key = "amount") {
            AmountHeader(state, onClick = { onAmountTapped(TransferField.AMOUNT) })
        }

        if (state.writeFailed) {
            item(key = "error") {
                Text(
                    text = stringResource(R.string.transaction_write_failed),
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.destructive,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CentsDimens.screenMargin),
                )
            }
        }

        if (state.canChangeKind) {
            item(key = "kind") {
                EntryKindControl(
                    selected = EntryKind.TRANSFER,
                    onSelect = { kind -> if (kind != EntryKind.TRANSFER) onSwitchKind(kind) },
                    modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
                )
            }
        }

        accountSections(state = state, viewModel = viewModel, onCreateAccount = onCreateAccount)

        details(
            state = state,
            viewModel = viewModel,
            keypad = keypad,
            onFeeTapped = { onAmountTapped(TransferField.FEE) },
        )

        if (state.isEditing) {
            item(key = "delete") {
                CentsListSection {
                    CentsActionRow(
                        label = stringResource(R.string.transfer_delete),
                        onClick = viewModel::requestDeletion,
                        destructive = true,
                    )
                }
            }
        }
    }
}

/**
 * Where the money leaves and where it lands.
 *
 * Two sections rather than one with two rows, because they are two separate choices and the
 * second one depends on the first. Between them sits **Swap**, which is one tap for the
 * mistake this form makes easiest to make.
 */
private fun LazyListScope.accountSections(
    state: TransferFormUiState,
    viewModel: TransferFormViewModel,
    onCreateAccount: () -> Unit,
) {
    if (state.needsSecondAccount) {
        item(key = "needs-account") {
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
        }
        return
    }

    item(key = "from") {
        PickerSection(
            header = stringResource(R.string.transfer_from),
            options = state.accounts.map { it.id to it.name },
            selectedId = state.fromAccountId,
            onSelect = viewModel::selectFrom,
        )
    }
    item(key = "to") {
        PickerSection(
            header = stringResource(R.string.transfer_to),
            options = state.destinations.map { it.id to it.name },
            selectedId = state.toAccountId,
            onSelect = viewModel::selectTo,
            action = stringResource(R.string.transfer_swap) to viewModel::swapAccounts,
        )
    }
}

/** When it happened, what it cost, and what it was for. */
private fun LazyListScope.details(
    state: TransferFormUiState,
    viewModel: TransferFormViewModel,
    keypad: AmountKeypadState,
    onFeeTapped: () -> Unit,
) {
    item(key = "details") {
        // With a fee typed, the footer stops explaining fees and says what this one does:
        // the amount above is what *arrives*, and the source account is short by more than
        // that. It is the one number on this form that is nowhere on it.
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
                        CentsTheme.colors.accent
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
}

@Composable
private fun AmountHeader(
    state: TransferFormUiState,
    onClick: () -> Unit,
) {
    Text(
        text = AmountEntry.format(state.amountInput, state.currency),
        style = CentsTheme.typography.largeTitle,
        color =
            if (state.typing == TransferField.AMOUNT) {
                CentsTheme.colors.label
            } else {
                CentsTheme.colors.secondaryLabel
            },
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(AMOUNT_HEADER_TAG)
                .clickable(onClick = onClick)
                .padding(vertical = CentsDimens.spacingL),
    )
}

/** A grouped section where exactly one row is ticked, optionally ending in an action. */
@Composable
private fun PickerSection(
    header: String,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    footer: String? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    CentsListSection(header = header, footer = footer) {
        options.forEachIndexed { index, (id, name) ->
            CentsListRow(
                title = name,
                value = if (id == selectedId) SELECTED_MARK else null,
                valueColor = CentsTheme.colors.accent,
                showSeparator = action != null || index != options.lastIndex,
                onClick = { onSelect(id) },
            )
        }
        if (action != null) {
            val (label, onClick) = action
            CentsActionRow(label = label, onClick = onClick, modifier = Modifier.testTag(SWAP_TAG))
        }
    }
}

private const val SELECTED_MARK = "✓"
internal const val NOTE_FIELD_TAG = "transferNote"
internal const val AMOUNT_HEADER_TAG = "transferAmount"
internal const val FEE_ROW_TAG = "transferFee"
internal const val SWAP_TAG = "transferSwap"

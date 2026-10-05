package io.github.anderescaray.cents.ui.feature.transactionform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsButton
import io.github.anderescaray.cents.ui.components.CentsChip
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsSheetAction
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.components.EntryKind
import io.github.anderescaray.cents.ui.components.EntryKindControl
import io.github.anderescaray.cents.ui.components.rememberAmountKeypadState
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.relativeLabel

/** Which picker the form has open over itself, if any. */
private enum class FormSheet { ACCOUNT, GROUP }

/**
 * The movement form, shaped like the film's sheet: what kind, how much in large type, the
 * category as one tap on a chip, then the keypad and one button that says what it will do —
 * *Add expense*. The account, the date, the group and a note sit as small chips between, already
 * answered with the usual answer, so the common case is amount, category, done.
 */
@Composable
fun TransactionFormScreen(
    onDone: () -> Unit,
    onCreateCategory: (CategoryKind) -> Unit,
    onCreateAccount: () -> Unit,
    onSwitchToTransfer: (String) -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keypad = rememberAmountKeypadState(shownInitially = viewModel.isNewRecord)
    val focusManager = LocalFocusManager.current
    var sheet by rememberSaveable { mutableStateOf<FormSheet?>(null) }

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedCard)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                // The note field is a text input inside a form that has a keypad pinned to the
                // bottom: without this the soft keyboard covers the field the owner just tapped.
                .imePadding(),
    ) {
        FormTopBar(
            title = stringResource(state.titleRes()),
            onCancel = onDone,
            onDelete = viewModel::requestDeletion.takeIf { state.isEditing },
        )

        FormBody(
            state = state,
            viewModel = viewModel,
            onSwitchToTransfer = onSwitchToTransfer,
            onAmountTapped = {
                // Clearing focus is what lowers the system keyboard; without it the two
                // would be stacked on each other.
                focusManager.clearFocus()
                keypad.showForAmount()
            },
            onCreateCategory = onCreateCategory,
            onAccount = { if (state.accounts.isEmpty()) onCreateAccount() else sheet = FormSheet.ACCOUNT },
            onGroup = { sheet = FormSheet.GROUP },
            onNoteFocus = keypad::onTextFieldFocus,
            modifier = Modifier.weight(1f),
        )

        FormFooter(
            keypadShown = keypad.isShown,
            confirmLabel = stringResource(state.confirmRes()),
            canSave = state.canSave,
            viewModel = viewModel,
        )
    }

    FormDialogs(state = state, viewModel = viewModel, sheet = sheet, onCloseSheet = { sheet = null })
}

/** Everything between the bar and the keypad: kind, amount, category, details and a note. */
@Composable
private fun FormBody(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    onSwitchToTransfer: (String) -> Unit,
    onAmountTapped: () -> Unit,
    onCreateCategory: (CategoryKind) -> Unit,
    onAccount: () -> Unit,
    onGroup: () -> Unit,
    onNoteFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        EntryKindControl(
            selected = if (state.kind == TransactionKind.INCOME) EntryKind.INCOME else EntryKind.EXPENSE,
            includeTransfer = state.canBecomeTransfer,
            onSelect = { entry ->
                when (entry) {
                    EntryKind.EXPENSE -> viewModel.setKind(TransactionKind.EXPENSE)

                    EntryKind.INCOME -> viewModel.setKind(TransactionKind.INCOME)

                    // The third answer is a different screen, because a transfer has two
                    // accounts and no category. It replaces this one rather than stacking
                    // on it — see `switchForm` in CentsApp. The amount travels with it.
                    EntryKind.TRANSFER -> onSwitchToTransfer(state.amountInput)
                }
            },
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
        )

        AmountHeader(state = state, onClick = onAmountTapped)

        if (state.writeFailed) {
            Text(
                text = stringResource(R.string.transaction_write_failed),
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.destructiveText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CentsDimens.screenMargin),
            )
        }

        CategoryChips(state = state, viewModel = viewModel, onCreateCategory = onCreateCategory)

        DetailChips(
            state = state,
            onAccount = onAccount,
            onDate = viewModel::openDatePicker,
            onGroup = onGroup,
        )

        NoteField(
            note = state.note,
            onNoteChange = viewModel::setNote,
            onFocus = onNoteFocus,
        )
    }
}

/** The keypad while the amount is being typed, and the one button that says what saving does. */
@Composable
private fun FormFooter(
    keypadShown: Boolean,
    confirmLabel: String,
    canSave: Boolean,
    viewModel: TransactionFormViewModel,
) {
    if (keypadShown) {
        CentsKeypad(
            onDigit = viewModel::appendDigit,
            onDecimalSeparator = viewModel::appendDecimalSeparator,
            onDelete = viewModel::deleteDigit,
            applyBottomInset = false,
        )
    }
    CentsButton(
        text = confirmLabel,
        onClick = viewModel::save,
        enabled = canSave,
        modifier =
            Modifier
                .padding(horizontal = CentsDimens.screenMargin)
                .padding(
                    top = if (keypadShown) CentsDimens.spacingXs else CentsDimens.spacingS,
                    bottom =
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + CentsDimens.spacingM,
                ),
    )
}

/** Cancel on the left, what this is in the middle, and Delete on the right when editing. */
@Composable
internal fun FormTopBar(
    title: String,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CentsDimens.screenMargin, vertical = CentsDimens.spacingXs),
    ) {
        Text(
            text = stringResource(R.string.action_cancel),
            style = CentsTheme.typography.callout,
            color = CentsTheme.colors.accentText,
            modifier = Modifier.align(Alignment.CenterStart).tappable(onClick = onCancel),
        )
        Text(
            text = title,
            style = CentsTheme.typography.headline,
            color = CentsTheme.colors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
        if (onDelete != null) {
            Text(
                text = stringResource(R.string.action_delete),
                style = CentsTheme.typography.callout,
                color = CentsTheme.colors.destructiveText,
                modifier = Modifier.align(Alignment.CenterEnd).tappable(onClick = onDelete),
            )
        }
    }
}

/** The amount, as the headline of the sheet. Tapping it asks for the keypad back. */
@Composable
private fun AmountHeader(
    state: TransactionFormUiState,
    onClick: () -> Unit,
) {
    Text(
        // The entry, not the amount: 25 reads "25 €" and 25, reads "25, €", so the comma key
        // and the digits behind it are visibly doing something.
        text = AmountEntry.format(state.amountInput, state.currency),
        style = CentsTheme.typography.display.copy(fontSize = AMOUNT_SIZE.sp, lineHeight = (AMOUNT_SIZE + 8).sp),
        color = if (state.kind == TransactionKind.INCOME) CentsTheme.colors.positiveText else CentsTheme.colors.label,
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

/** What the form puts on top of itself: the calendar, the two pickers, and the deletion. */
@Composable
private fun FormDialogs(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    sheet: FormSheet?,
    onCloseSheet: () -> Unit,
) {
    if (state.pickingDate) {
        CentsDatePicker(
            selected = state.date,
            today = state.today,
            onSelect = viewModel::setDate,
            onDismiss = viewModel::dismissDatePicker,
        )
    }

    when (sheet) {
        FormSheet.ACCOUNT -> AccountSheet(state, viewModel, onCloseSheet)
        FormSheet.GROUP -> GroupSheet(state, viewModel, onCloseSheet)
        null -> Unit
    }

    if (state.confirmingDeletion) {
        CentsAlert(
            title = stringResource(R.string.transaction_delete_title),
            message = stringResource(R.string.transaction_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::confirmDeletion,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::cancelDeletion,
            destructive = true,
        )
    }
}

private fun TransactionFormUiState.titleRes(): Int =
    when {
        isEditing -> R.string.edit_transaction_title
        kind == TransactionKind.INCOME -> R.string.form_title_new_income
        else -> R.string.form_title_new_expense
    }

private fun TransactionFormUiState.confirmRes(): Int =
    when {
        isEditing -> R.string.action_save
        kind == TransactionKind.INCOME -> R.string.form_add_income
        else -> R.string.form_add_expense
    }

private const val AMOUNT_SIZE = 52
private const val AMOUNT_MIN_SIZE = 20
private val NOTE_RADIUS = CentsDimens.keyRadius

internal const val NOTE_FIELD_TAG = "transactionNote"
internal const val AMOUNT_HEADER_TAG = "transactionAmount"

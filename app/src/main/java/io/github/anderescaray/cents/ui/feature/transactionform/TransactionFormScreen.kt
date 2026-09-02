package io.github.anderescaray.cents.ui.feature.transactionform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.AmountKeypadState
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.components.rememberAmountKeypadState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.relativeLabel

@Composable
fun TransactionFormScreen(
    onDone: () -> Unit,
    onCreateCategory: (CategoryKind) -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel(),
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
                // The note field is the app's first text input inside a form that already
                // has a keypad pinned to the bottom: without this the soft keyboard covers
                // the field the owner just tapped.
                .imePadding(),
    ) {
        CentsModalBar(
            title =
                stringResource(
                    if (state.isEditing) R.string.edit_transaction_title else R.string.add_transaction_title,
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
            onAmountTapped = {
                // Clearing focus is what lowers the system keyboard; without it the two
                // would be stacked on each other.
                focusManager.clearFocus()
                keypad.showForAmount()
            },
            onCreateCategory = onCreateCategory,
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
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
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

/** Amount, direction, account and category: everything above the keypad. */
@Composable
private fun FormFields(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    keypad: AmountKeypadState,
    onAmountTapped: () -> Unit,
    onCreateCategory: (CategoryKind) -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        item(key = "amount") { AmountHeader(state, onClick = onAmountTapped) }

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

        item(key = "kind") {
            // Resolved here: a lambda passed to a component is not a composable scope.
            val expenseLabel = stringResource(R.string.kind_expense)
            val incomeLabel = stringResource(R.string.kind_income)
            CentsSegmentedControl(
                options = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME),
                selected = state.kind,
                onSelect = viewModel::setKind,
                label = { kind -> if (kind == TransactionKind.EXPENSE) expenseLabel else incomeLabel },
                modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
            )
        }

        accountSection(state = state, viewModel = viewModel, onCreateAccount = onCreateAccount)

        details(state = state, viewModel = viewModel, keypad = keypad)

        categorySection(state = state, viewModel = viewModel, onCreateCategory = onCreateCategory)

        if (state.isEditing) {
            deleteAction(onDelete = viewModel::requestDeletion)
        }
    }
}

/** Where the money moved. Without an account there is nowhere to record anything. */
private fun LazyListScope.accountSection(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    onCreateAccount: () -> Unit,
) {
    item(key = "account") {
        if (state.accounts.isEmpty()) {
            CentsListSection(
                header = stringResource(R.string.field_account),
                footer = stringResource(R.string.field_account_empty_footer),
            ) {
                CentsListRow(
                    title = stringResource(R.string.field_account_empty),
                    showChevron = true,
                    showSeparator = false,
                    onClick = onCreateAccount,
                )
            }
        } else {
            PickerSection(
                header = stringResource(R.string.field_account),
                options = state.accounts.map { it.id to it.name },
                selectedId = state.selectedAccountId,
                onSelect = viewModel::selectAccount,
            )
        }
    }
}

/** When it happened and what it was for: the two fields the keypad cannot type. */
private fun LazyListScope.details(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    keypad: AmountKeypadState,
) {
    item(key = "details") {
        CentsListSection {
            CentsListRow(
                title = stringResource(R.string.field_date),
                value = state.date.relativeLabel(state.today),
                showChevron = true,
                onClick = viewModel::openDatePicker,
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

private fun LazyListScope.categorySection(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    onCreateCategory: (CategoryKind) -> Unit,
) {
    item(key = "category") {
        if (state.categories.isEmpty()) {
            // Nothing is seeded, so an empty list is the normal first-run state: the way
            // out of it is creating a category, not staring at an empty box.
            CentsListSection(
                header = stringResource(R.string.field_category),
                footer = stringResource(R.string.field_category_empty_footer),
            ) {
                CentsListRow(
                    title = stringResource(R.string.field_category_empty),
                    showChevron = true,
                    showSeparator = false,
                    onClick = { onCreateCategory(state.kind.toCategoryKind()) },
                )
            }
        } else {
            PickerSection(
                header = stringResource(R.string.field_category),
                footer = stringResource(R.string.field_category_footer),
                options = state.categories.map { it.id to it.name },
                selectedId = state.selectedCategoryId,
                onSelect = { id ->
                    viewModel.selectCategory(id.takeIf { it != state.selectedCategoryId })
                },
            )
        }
    }
}

/** Removing a movement that should never have been recorded. */
private fun LazyListScope.deleteAction(onDelete: () -> Unit) {
    item(key = "delete") {
        CentsListSection {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(CentsTheme.colors.groupedCard)
                        .clickable(onClick = onDelete)
                        .padding(vertical = CentsDimens.spacingM),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.transaction_delete),
                    style = CentsTheme.typography.body,
                    color = CentsTheme.colors.destructive,
                )
            }
        }
    }
}

@Composable
private fun AmountHeader(
    state: TransactionFormUiState,
    onClick: () -> Unit,
) {
    Text(
        // The entry, not the amount: 25 reads "25 €" and 25, reads "25, €", so the comma key
        // and the digits behind it are visibly doing something.
        text = AmountEntry.format(state.amountInput, state.currency),
        style = CentsTheme.typography.largeTitle,
        color =
            if (state.kind == TransactionKind.INCOME) {
                CentsTheme.colors.positive
            } else {
                CentsTheme.colors.label
            },
        textAlign = TextAlign.Center,
        // The amount is how you ask for the keypad back once a text field has taken it away.
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(AMOUNT_HEADER_TAG)
                .clickable(onClick = onClick)
                .padding(vertical = CentsDimens.spacingL),
    )
}

/** A grouped section where exactly one row is ticked. */
@Composable
private fun PickerSection(
    header: String,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    footer: String? = null,
) {
    CentsListSection(header = header, footer = footer) {
        options.forEachIndexed { index, (id, name) ->
            CentsListRow(
                title = name,
                value = if (id == selectedId) SELECTED_MARK else null,
                valueColor = CentsTheme.colors.accent,
                showSeparator = index != options.lastIndex,
                onClick = { onSelect(id) },
            )
        }
    }
}

private const val SELECTED_MARK = "✓"
internal const val NOTE_FIELD_TAG = "transactionNote"
internal const val AMOUNT_HEADER_TAG = "transactionAmount"

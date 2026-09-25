package io.github.anderescaray.cents.ui.feature.recurring

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
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.AmountKeypadState
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.components.CentsSheetAction
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.components.rememberAmountKeypadState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.relativeLabel

/**
 * Defining something that repeats.
 *
 * The same modal shape as the movement form, and mostly the same fields — because a rule *is* a
 * movement, plus how often. The two things it adds are the frequency and the start date, and
 * the start date is the one worth reading twice: it anchors every occurrence, so a rule entered
 * today with a start date in the past will write the charges it missed the moment it is saved.
 */
@Composable
fun RuleFormScreen(
    onDone: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: RuleFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keypad = rememberAmountKeypadState(shownInitially = !state.isEditing)
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
            title = stringResource(if (state.isEditing) R.string.rule_edit_title else R.string.rule_new_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )

        RuleFields(
            state = state,
            viewModel = viewModel,
            keypad = keypad,
            onAmountTapped = {
                focusManager.clearFocus()
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

    RuleDialogs(state = state, viewModel = viewModel)
}

/** The calendar, and the question asked before a rule stops. */
@Composable
private fun RuleDialogs(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
) {
    if (state.pickingFrequency) {
        FrequencySheet(
            selected = state.frequency,
            intervalDays = state.intervalDays,
            onSelect = viewModel::setFrequency,
            onDismiss = viewModel::dismissFrequencyPicker,
        )
    }

    state.pickingDate?.let { field ->
        CentsDatePicker(
            selected = if (field == RuleDateField.START) state.startDate else state.endDate ?: state.startDate,
            today = state.today,
            onSelect = viewModel::setDate,
            onDismiss = viewModel::dismissDatePicker,
        )
    }

    if (state.confirmingRemoval) {
        // Two questions behind one button: a running subscription with charges behind it is
        // stopped, and a stopped one — or one that never charged anything — is removed.
        CentsAlert(
            title =
                stringResource(
                    if (state.stopsRatherThanDeletes) R.string.rule_stop_title else R.string.rule_delete_title,
                ),
            message = removalMessage(state),
            confirmLabel =
                stringResource(
                    if (state.stopsRatherThanDeletes) R.string.rule_stop_action else R.string.action_delete,
                ),
            onConfirm = viewModel::confirmRemoval,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::cancelRemoval,
            destructive = true,
        )
    }
}

/**
 * What the confirmation says, which is different in all three cases.
 *
 * The one worth spelling out is deleting a rule that *did* charge: the movements stay, and
 * saying so is the difference between a confident tap and a cancelled one.
 */
@Composable
private fun removalMessage(state: RuleFormUiState): String =
    when {
        state.stopsRatherThanDeletes -> stringResource(R.string.rule_stop_message, state.generatedCount)
        state.hasHistory -> stringResource(R.string.rule_delete_kept_message, state.generatedCount)
        else -> stringResource(R.string.rule_delete_message)
    }

/**
 * The six frequencies, on a sheet.
 *
 * The one already chosen is marked rather than removed: a sheet whose contents depend on the
 * current answer makes the owner count the rows to work out what they picked last time.
 */
@Composable
private fun FrequencySheet(
    selected: Frequency,
    intervalDays: Int?,
    onSelect: (Frequency) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = stringResource(R.string.rule_frequency_chosen)
    CentsActionSheet(
        title = stringResource(R.string.rule_field_frequency),
        message = stringResource(R.string.rule_frequency_sheet_message),
        actions =
            Frequency.entries.map { frequency ->
                val label = frequencyLabel(frequency, intervalDays)
                CentsSheetAction(
                    label = if (frequency == selected) "$label $chosen" else label,
                    onClick = { onSelect(frequency) },
                )
            },
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
    )
}

@Composable
private fun RuleFields(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
    keypad: AmountKeypadState,
    onAmountTapped: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        item(key = "amount") { AmountHeader(state, onClick = onAmountTapped) }

        if (state.writeFailed) {
            item(key = "error") { WriteFailure() }
        }

        item(key = "kind") { KindControl(state, viewModel) }
        item(key = "name") { NameSection(state, viewModel, keypad) }
        item(key = "schedule") { ScheduleSection(state, viewModel) }
        if (state.isEditing && state.ruleActive) {
            item(key = "next") { NextOneSection() }
        }
        remindersSection(state, viewModel)
        accountSection(state, viewModel, onCreateAccount)
        categorySection(state, viewModel)
        item(key = "mode") { ModeSection(state, viewModel) }

        if (state.isEditing) {
            removalAction(state, onRemove = viewModel::requestRemoval)
        }
    }
}

@Composable
private fun AmountHeader(
    state: RuleFormUiState,
    onClick: () -> Unit,
) {
    Text(
        text = AmountEntry.format(state.amountInput, state.currency),
        style = CentsTheme.typography.largeTitle,
        color =
            if (state.kind == TransactionKind.INCOME) CentsTheme.colors.positive else CentsTheme.colors.label,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(RULE_AMOUNT_TAG)
                .clickable(onClick = onClick)
                .padding(vertical = CentsDimens.spacingL),
    )
}

@Composable
private fun WriteFailure() {
    Text(
        text = stringResource(R.string.rule_write_failed),
        style = CentsTheme.typography.footnote,
        color = CentsTheme.colors.destructive,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CentsDimens.screenMargin),
    )
}

/**
 * Money out or money in. A salary is a recurring rule too (PRD §4.4), and the only thing that
 * changes is which categories are offered — and that it is left out of the monthly total, which
 * is about what is going out.
 */
@Composable
private fun KindControl(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
) {
    val expense = stringResource(R.string.kind_expense)
    val income = stringResource(R.string.kind_income)
    CentsSegmentedControl(
        options = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME),
        selected = state.kind,
        onSelect = viewModel::setKind,
        label = { option -> if (option == TransactionKind.EXPENSE) expense else income },
        modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
    )
}

/** What it is called: the name the movements it makes will carry. */
@Composable
private fun NameSection(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
    keypad: AmountKeypadState,
) {
    CentsListSection(footer = stringResource(R.string.rule_field_name_footer)) {
        CentsTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            placeholder = stringResource(R.string.rule_field_name_placeholder),
            modifier =
                Modifier
                    .testTag(RULE_NAME_TAG)
                    .onFocusChanged { keypad.onTextFieldFocus(it.isFocused) },
        )
    }
}

/**
 * How often, from when, and until when.
 *
 * **The frequency is one row that opens a sheet**, not six rows that are always there. Six
 * permanent rows for a choice that is made once and then read at a glance pushed the two fields
 * that actually need thinking about — the dates — off the bottom of the screen.
 *
 * The footer under the start date says what it will do rather than what it is, because "1 May"
 * on a rule created in September is five months of backlog and that is not obvious from a date.
 */
@Composable
private fun ScheduleSection(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
) {
    CentsListSection(footer = stringResource(R.string.rule_field_start_footer)) {
        CentsListRow(
            title = stringResource(R.string.rule_field_frequency),
            value = frequencyLabel(state.frequency, state.intervalDays),
            showChevron = true,
            onClick = viewModel::openFrequencyPicker,
        )
        if (state.needsInterval) {
            CentsTextField(
                value = state.intervalInput,
                onValueChange = viewModel::setInterval,
                placeholder = stringResource(R.string.rule_field_interval_placeholder),
                showSeparator = true,
            )
        }
        CentsListRow(
            title = stringResource(R.string.rule_field_start),
            value = state.startDate.relativeLabel(state.today),
            showChevron = true,
            onClick = { viewModel.openDatePicker(RuleDateField.START) },
        )
        CentsListRow(
            title = stringResource(R.string.rule_field_end),
            value =
                state.endDate?.relativeLabel(state.today)
                    ?: stringResource(R.string.rule_field_end_none),
            showChevron = true,
            showSeparator = state.endDate != null,
            onClick = { viewModel.openDatePicker(RuleDateField.END) },
        )
        if (state.endDate != null) {
            CentsListRow(
                title = stringResource(R.string.rule_field_end_clear),
                showSeparator = false,
                onClick = viewModel::clearEndDate,
            )
        }
    }
}

internal const val RULE_AMOUNT_TAG = "ruleAmount"
internal const val RULE_NAME_TAG = "ruleName"

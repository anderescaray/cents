package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel

/**
 * The week's values (`DECISIONS.md` D40): one field per open asset for what it is worth now, the
 * change since its last worth written under it as it is typed, and one date for all of them.
 */
@Composable
fun ValueUpdateScreen(
    onDone: () -> Unit,
    viewModel: ValueUpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .imePadding(),
    ) {
        CentsModalBar(
            title = stringResource(R.string.values_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = CentsDimens.sectionSpacing),
        ) {
            item(key = "date") {
                CentsListSection(footer = stringResource(R.string.values_footer)) {
                    CentsListRow(
                        title = stringResource(R.string.trade_field_date),
                        value = state.date.relativeLabel(state.today),
                        showSeparator = false,
                        onClick = viewModel::openDatePicker,
                    )
                }
            }
            state.entries.forEach { entry ->
                item(
                    key = entry.position.holding.id,
                ) { ValueField(entry) { viewModel.onValueTyped(entry.position.holding.id, it) } }
            }
        }
    }
    if (state.pickingDate) {
        CentsDatePicker(
            selected = state.date,
            today = state.today,
            onSelect = viewModel::onDateSelected,
            onDismiss = viewModel::dismissDatePicker,
        )
    }
    if (state.writeFailed) {
        CentsAlert(
            title = stringResource(R.string.delete_failed_title),
            message = stringResource(R.string.values_save_failed_message),
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = viewModel::dismissWriteFailure,
            onDismiss = viewModel::dismissWriteFailure,
        )
    }
}

@Composable
private fun ValueField(
    entry: ValueEntry,
    onTyped: (String) -> Unit,
) {
    val worth = MoneyFormatter.format(entry.position.value)
    CentsListSection(
        header = entry.position.holding.name,
        footer =
            entry.change?.let { stringResource(R.string.values_change, MoneyFormatter.formatSigned(it), worth) }
                ?: stringResource(R.string.values_current, worth),
    ) {
        CentsTextField(
            value = AmountEntry.toTypedText(entry.input),
            onValueChange = onTyped,
            placeholder = stringResource(R.string.values_placeholder),
            numeric = true,
            modifier = Modifier.testTag(VALUE_FIELD_TAG),
        )
    }
}

internal const val VALUE_FIELD_TAG = "valueField"

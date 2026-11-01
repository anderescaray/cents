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
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsSwitch
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel

/**
 * A purchase or a sale (`DECISIONS.md` D40): an amount and a date, and for a sale whether it
 * empties the asset. A new sale says what it will make before it is saved.
 */
@Composable
fun TradeFormScreen(
    onDone: () -> Unit,
    viewModel: TradeFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    val selling = state.side == TradeSide.SELL

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .imePadding(),
    ) {
        CentsModalBar(
            title = stringResource(if (selling) R.string.trade_sell_title else R.string.trade_buy_title),
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
            item(key = "fields") { TradeFields(state, viewModel) }
            if (selling) item(key = "all") { SellsAll(state, viewModel::onSellsAllChanged) }
            item(key = "note") {
                CentsListSection(header = stringResource(R.string.field_note_placeholder)) {
                    CentsTextField(
                        value = state.note,
                        onValueChange = viewModel::onNoteChanged,
                        placeholder = stringResource(R.string.holding_field_note_placeholder),
                    )
                }
            }
            if (!state.isNew) {
                item(key = "delete") {
                    CentsActionRow(
                        label = stringResource(R.string.trade_delete),
                        onClick = viewModel::requestDeletion,
                        destructive = true,
                    )
                }
            }
        }
    }
    TradeFormDialogs(state, viewModel)
}

@Composable
private fun TradeFields(
    state: TradeFormUiState,
    viewModel: TradeFormViewModel,
) {
    val selling = state.side == TradeSide.SELL
    CentsListSection(
        footer =
            state.resultPreview?.let {
                stringResource(R.string.trade_result_preview, MoneyFormatter.formatSigned(it))
            },
    ) {
        CentsTextField(
            value = AmountEntry.toTypedText(state.amountInput),
            onValueChange = viewModel::onAmountTyped,
            placeholder =
                stringResource(
                    if (selling) R.string.trade_field_amount_sell else R.string.trade_field_amount_buy,
                ),
            numeric = true,
            showSeparator = true,
            modifier = Modifier.testTag(AMOUNT_FIELD_TAG),
        )
        CentsListRow(
            title = stringResource(R.string.trade_field_date),
            value = state.date.relativeLabel(state.today),
            showSeparator = false,
            onClick = viewModel::openDatePicker,
        )
    }
}

@Composable
private fun SellsAll(
    state: TradeFormUiState,
    onChange: (Boolean) -> Unit,
) {
    CentsListSection(footer = stringResource(R.string.trade_sells_all_footer)) {
        CentsListRow(
            title = stringResource(R.string.trade_sells_all),
            showSeparator = false,
            trailing = {
                CentsSwitch(
                    checked = state.sellsAll,
                    onCheckedChange = onChange,
                    modifier = Modifier.testTag(SELLS_ALL_TAG),
                )
            },
        )
    }
}

@Composable
private fun TradeFormDialogs(
    state: TradeFormUiState,
    viewModel: TradeFormViewModel,
) {
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
            message = stringResource(R.string.trade_save_failed_message),
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = viewModel::dismissWriteFailure,
            onDismiss = viewModel::dismissWriteFailure,
        )
    }
    if (state.deleting) {
        CentsAlert(
            title = stringResource(R.string.trade_delete_title),
            message = stringResource(R.string.trade_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::confirmDeletion,
            onDismiss = viewModel::cancelDeletion,
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

internal const val SELLS_ALL_TAG = "sellsAll"

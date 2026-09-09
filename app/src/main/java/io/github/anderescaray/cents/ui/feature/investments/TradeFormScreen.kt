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
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.QuantityEntry
import io.github.anderescaray.cents.ui.util.relativeLabel

/**
 * Recording a purchase or a sale.
 *
 * The footer under the fields says what the trade actually moves, fee included — the one number
 * the owner can check against their broker's confirmation, and the one this screen would
 * otherwise leave them to work out in their head.
 */
@Composable
fun TradeFormScreen(
    onDone: () -> Unit,
    viewModel: TradeFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            title = stringResource(if (state.isNew) R.string.trade_new_title else R.string.trade_edit_title),
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
            item(key = "side") { SideControl(state.side, viewModel::onSideChanged) }
            item(key = "fields") { TradeFields(state, viewModel) }

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

    if (state.pickingDate) {
        CentsDatePicker(
            selected = state.date,
            today = state.today,
            onSelect = viewModel::onDateSelected,
            onDismiss = viewModel::dismissDatePicker,
        )
    }

    TradeFormDialogs(state = state, viewModel = viewModel)
}

/**
 * The date picker and the two alerts. Out of the screen function because they are what it does
 * *after* laying anything out, and reading the layout should not mean scrolling past them.
 */
@Composable
private fun TradeFormDialogs(
    state: TradeFormUiState,
    viewModel: TradeFormViewModel,
) {
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

@Composable
private fun SideControl(
    side: TradeSide,
    onSelect: (TradeSide) -> Unit,
) {
    // The two labels are read here rather than inside the control: what it takes is a plain
    // function from an option to a string, and a resource lookup is a composable call that
    // cannot happen inside one.
    val buy = stringResource(R.string.trade_side_buy)
    val sell = stringResource(R.string.trade_side_sell)
    CentsSegmentedControl(
        options = TradeSide.entries,
        selected = side,
        onSelect = onSelect,
        label = { option -> if (option == TradeSide.BUY) buy else sell },
        modifier = Modifier.padding(horizontal = CentsDimens.screenMargin, vertical = CentsDimens.spacingM),
    )
}

@Composable
private fun TradeFields(
    state: TradeFormUiState,
    viewModel: TradeFormViewModel,
) {
    CentsListSection(footer = footerFor(state)) {
        CentsTextField(
            value = QuantityEntry.toTypedText(state.quantityInput),
            onValueChange = viewModel::onQuantityTyped,
            placeholder = stringResource(R.string.trade_field_quantity_placeholder),
            numeric = true,
            showSeparator = true,
        )
        CentsTextField(
            value = AmountEntry.toTypedText(state.priceInput),
            onValueChange = viewModel::onPriceTyped,
            placeholder = stringResource(R.string.trade_field_price),
            numeric = true,
            showSeparator = true,
        )
        CentsTextField(
            value = AmountEntry.toTypedText(state.feeInput),
            onValueChange = viewModel::onFeeTyped,
            placeholder = stringResource(R.string.trade_field_fee),
            numeric = true,
            showSeparator = true,
        )
        CentsListRow(
            title = stringResource(R.string.trade_field_date),
            value = state.date.relativeLabel(state.today),
            showSeparator = false,
            onClick = viewModel::openDatePicker,
        )
    }
}

/**
 * What the fields add up to — or, when a sale is bigger than the position, why it cannot be
 * saved. The refusal takes the footer rather than appearing beside it: it is the only thing
 * worth reading at that moment.
 */
@Composable
private fun footerFor(state: TradeFormUiState): String =
    when {
        state.sellsMoreThanHeld -> {
            stringResource(R.string.trade_too_much, QuantityEntry.format(state.heldElsewhere))
        }

        state.side == TradeSide.BUY -> {
            stringResource(R.string.trade_total_buy, MoneyFormatter.format(state.total))
        }

        else -> {
            stringResource(R.string.trade_total_sell, MoneyFormatter.format(state.total))
        }
    }

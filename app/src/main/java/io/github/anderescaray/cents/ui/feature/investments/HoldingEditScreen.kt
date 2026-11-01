package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsChip
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.relativeLabel

/**
 * A new asset, or an existing one (`DECISIONS.md` D40): a type, a name of the owner's own, and —
 * for a new one — the broker it goes in and its first purchase, a date and an amount.
 */
@Composable
fun HoldingEditScreen(
    onDone: () -> Unit,
    onCreateBrokerAccount: () -> Unit,
    viewModel: HoldingEditViewModel = hiltViewModel(),
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
            title = stringResource(if (state.isNew) R.string.holding_new_title else R.string.holding_edit_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )
        HoldingEditFields(state, viewModel, onCreateBrokerAccount, Modifier.weight(1f))
    }
    HoldingEditDialogs(state, viewModel)
}

/** The form's sections: the broker to create first if there is none, else type, name, purchase and note. */
@Composable
private fun HoldingEditFields(
    state: HoldingEditUiState,
    viewModel: HoldingEditViewModel,
    onCreateBrokerAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = CentsDimens.sectionSpacing),
    ) {
        if (state.needsBrokerAccount) {
            item(key = "broker") {
                CentsListSection(footer = stringResource(R.string.holding_needs_broker)) {
                    CentsActionRow(
                        label = stringResource(R.string.investments_create_broker),
                        onClick = onCreateBrokerAccount,
                    )
                }
            }
            return@LazyColumn
        }
        item(key = "class") { AssetClassChips(state.assetClass, viewModel::onAssetClassChanged) }
        item(key = "name") {
            CentsListSection {
                CentsTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChanged,
                    placeholder = stringResource(R.string.holding_field_name_placeholder),
                    modifier = Modifier.testTag(NAME_FIELD_TAG),
                )
            }
        }
        if (state.isNew) {
            item(key = "account") { AccountChips(state, viewModel::onAccountChanged) }
            item(key = "purchase") { FirstPurchase(state, viewModel) }
        }
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
                    label = stringResource(R.string.holding_delete),
                    onClick = viewModel::requestDeletion,
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun HoldingEditDialogs(
    state: HoldingEditUiState,
    viewModel: HoldingEditViewModel,
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
            message = stringResource(R.string.holding_save_failed_message),
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = viewModel::dismissWriteFailure,
            onDismiss = viewModel::dismissWriteFailure,
        )
    }
    if (state.deleting) {
        CentsAlert(
            title = stringResource(R.string.holding_delete_title, state.name),
            message = pluralStringResource(R.plurals.holding_delete_message_trades, state.tradeCount, state.tradeCount),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::confirmDeletion,
            onDismiss = viewModel::cancelDeletion,
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

/** The four types, as chips: one tap, and every label fits whatever the language. */
@Composable
private fun AssetClassChips(
    selected: AssetClass,
    onSelect: (AssetClass) -> Unit,
) {
    CentsListSection(header = stringResource(R.string.holding_field_class)) {
        FlowRow(
            modifier = Modifier.padding(CentsDimens.spacingM),
            horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
        ) {
            AssetClass.entries.forEach { assetClass ->
                CentsChip(
                    label = assetClassLabel(assetClass),
                    selected = assetClass == selected,
                    onClick = { onSelect(assetClass) },
                )
            }
        }
    }
}

/** Which broker, offered only when there is more than one to choose from. */
@Composable
private fun AccountChips(
    state: HoldingEditUiState,
    onSelect: (Long) -> Unit,
) {
    if (state.accounts.size < 2) return
    CentsListSection(header = stringResource(R.string.holding_field_account)) {
        FlowRow(
            modifier = Modifier.padding(CentsDimens.spacingM),
            horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
        ) {
            state.accounts.forEach { account ->
                CentsChip(
                    label = account.name,
                    selected = account.id == state.accountId,
                    onClick = { onSelect(account.id) },
                )
            }
        }
    }
}

@Composable
private fun FirstPurchase(
    state: HoldingEditUiState,
    viewModel: HoldingEditViewModel,
) {
    CentsListSection(
        header = stringResource(R.string.holding_first_purchase_header),
        footer = stringResource(R.string.holding_first_purchase_footer),
    ) {
        CentsTextField(
            value = AmountEntry.toTypedText(state.amountInput),
            onValueChange = viewModel::onAmountTyped,
            placeholder = stringResource(R.string.trade_field_amount_buy),
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

internal const val NAME_FIELD_TAG = "assetName"
internal const val AMOUNT_FIELD_TAG = "tradeAmount"

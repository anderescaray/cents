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
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.feature.filters.SelectableRow
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * Creating or editing one holding.
 *
 * A modal form rather than a pushed screen, the same shape the account and category editors
 * use: it is a thing you fill in and finish, not a place you browse.
 */
@Composable
fun HoldingEditScreen(
    onDone: () -> Unit,
    viewModel: HoldingEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    if (state.writeFailed) {
        // The database refused the write — the symbol was taken between the check and the save.
        // Nothing is retried automatically: the owner asks again if they still want to.
        CentsAlert(
            title = stringResource(R.string.delete_failed_title),
            message = stringResource(R.string.holding_save_failed_message),
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = viewModel::dismissWriteFailure,
            onDismiss = viewModel::dismissWriteFailure,
        )
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
            title = stringResource(if (state.isNew) R.string.holding_new_title else R.string.holding_edit_title),
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
            item(key = "identity") { IdentitySection(state, viewModel) }
            item(key = "class") { AssetClassSection(state, viewModel) }
            item(key = "account") { AccountSection(state, viewModel) }
            item(key = "note") { NoteSection(state, viewModel) }
        }
    }
}

/**
 * What the holding is. The footer carries the duplicate warning when there is one, because a
 * symbol already held in this account is the only thing worth reading at that moment.
 */
@Composable
private fun IdentitySection(
    state: HoldingEditUiState,
    viewModel: HoldingEditViewModel,
) {
    CentsListSection(
        footer =
            state.duplicateOf?.let { stringResource(R.string.holding_duplicate, it) }
                ?: stringResource(R.string.holding_field_symbol_footer),
    ) {
        CentsTextField(
            value = state.symbol,
            onValueChange = viewModel::onSymbolChanged,
            placeholder = stringResource(R.string.holding_field_symbol_placeholder),
            showSeparator = true,
        )
        CentsTextField(
            value = state.name,
            onValueChange = viewModel::onNameChanged,
            placeholder = stringResource(R.string.holding_field_name_placeholder),
        )
    }
}

@Composable
private fun AssetClassSection(
    state: HoldingEditUiState,
    viewModel: HoldingEditViewModel,
) {
    CentsListSection(header = stringResource(R.string.holding_field_class)) {
        AssetClass.entries.forEachIndexed { index, assetClass ->
            key(assetClass) {
                SelectableRow(
                    title = assetClassLabel(assetClass),
                    selected = assetClass == state.assetClass,
                    onClick = { viewModel.onAssetClassChanged(assetClass) },
                    last = index == AssetClass.entries.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun AccountSection(
    state: HoldingEditUiState,
    viewModel: HoldingEditViewModel,
) {
    CentsListSection(
        header = stringResource(R.string.holding_field_account),
        footer = stringResource(R.string.holding_field_account_footer),
    ) {
        state.accounts.forEachIndexed { index, account ->
            key(account.id) {
                SelectableRow(
                    title = account.name,
                    selected = account.id == state.accountId,
                    onClick = { viewModel.onAccountChanged(account.id) },
                    last = index == state.accounts.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun NoteSection(
    state: HoldingEditUiState,
    viewModel: HoldingEditViewModel,
) {
    CentsListSection(header = stringResource(R.string.holding_field_note)) {
        CentsTextField(
            value = state.note,
            onValueChange = viewModel::onNoteChanged,
            placeholder = stringResource(R.string.holding_field_note_placeholder),
        )
    }
}

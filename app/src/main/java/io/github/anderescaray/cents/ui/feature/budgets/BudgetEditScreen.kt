package io.github.anderescaray.cents.ui.feature.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.rememberAmountKeypadState
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry

/** Setting one monthly limit: how much, and on which expense category. */
@Composable
fun BudgetEditScreen(
    onDone: () -> Unit,
    viewModel: BudgetEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keypad = rememberAmountKeypadState(shownInitially = state.isNew)

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        CentsModalBar(
            title = stringResource(if (state.isNew) R.string.budget_new_title else R.string.budget_edit_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
        ) {
            item(key = "limit") {
                LimitHeader(state = state, onTapped = keypad::showForAmount)
            }
            categorySection(state = state, onChoose = viewModel::chooseCategory)
            if (!state.isNew) {
                item(key = "delete") {
                    CentsListSection {
                        CentsActionRow(
                            label = stringResource(R.string.budget_delete),
                            onClick = viewModel::requestDelete,
                            destructive = true,
                        )
                    }
                }
            }
        }

        if (keypad.isShown) {
            CentsKeypad(
                onDigit = viewModel::appendDigit,
                onDecimalSeparator = viewModel::appendDecimalSeparator,
                onDelete = viewModel::deleteDigit,
            )
        }
    }

    if (state.confirmingDelete) {
        DeleteBudgetAlert(onConfirm = viewModel::confirmDelete, onDismiss = viewModel::cancelDelete)
    }
}

@Composable
private fun DeleteBudgetAlert(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CentsAlert(
        title = stringResource(R.string.budget_delete_title),
        message = stringResource(R.string.budget_delete_message),
        confirmLabel = stringResource(R.string.action_delete),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        destructive = true,
    )
}

@Composable
private fun LimitHeader(
    state: BudgetEditUiState,
    onTapped: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = CentsDimens.spacingL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingXs),
    ) {
        Text(
            text = AmountEntry.format(state.amountInput, state.currency),
            style = CentsTheme.typography.largeTitle,
            color = CentsTheme.colors.label,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .testTag(LIMIT_TAG)
                    .tappable(onClick = onTapped)
                    .padding(CentsDimens.spacingXs),
        )
        Text(
            text = stringResource(R.string.budget_per_month),
            style = CentsTheme.typography.footnote,
            color = CentsTheme.colors.secondaryLabel,
        )
    }
}

/**
 * Which category the limit is on. Offered as a list while the budget is new — every expense
 * category without one — and shown, not offered, once it exists.
 */
private fun LazyListScope.categorySection(
    state: BudgetEditUiState,
    onChoose: (Long) -> Unit,
) {
    item(key = "category") {
        val footer =
            when {
                state.writeFailed -> stringResource(R.string.budget_write_failed)
                !state.isNew -> stringResource(R.string.budget_category_footer_fixed)
                state.loaded && state.choices.isEmpty() -> stringResource(R.string.budget_no_choices)
                else -> stringResource(R.string.budget_category_footer_new)
            }
        CentsListSection(header = stringResource(R.string.budget_category_header), footer = footer) {
            val shown = if (state.isNew) state.choices else listOfNotNull(state.category)
            shown.forEachIndexed { index, category ->
                CentsListRow(
                    title = category.name,
                    selected = category.id == state.categoryId,
                    showSeparator = index != shown.lastIndex,
                    onClick = if (state.isNew) ({ onChoose(category.id) }) else null,
                )
            }
        }
    }
}

internal const val LIMIT_TAG = "budgetLimit"

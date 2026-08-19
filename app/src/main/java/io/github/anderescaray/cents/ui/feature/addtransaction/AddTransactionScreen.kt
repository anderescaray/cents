package io.github.anderescaray.cents.ui.feature.addtransaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter

@Composable
fun AddTransactionScreen(
    onDone: () -> Unit,
    onManageCategories: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        CentsModalBar(
            title = stringResource(R.string.add_transaction_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )

        FormFields(
            state = state,
            viewModel = viewModel,
            onManageCategories = onManageCategories,
            modifier = Modifier.weight(1f),
        )

        CentsKeypad(
            onDigit = viewModel::appendDigit,
            onDelete = viewModel::deleteDigit,
            modifier =
                Modifier.padding(
                    bottom =
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            CentsDimens.spacingS,
                ),
        )
    }
}

/** Amount, direction, account and category: everything above the keypad. */
@Composable
private fun FormFields(
    state: AddTransactionUiState,
    viewModel: AddTransactionViewModel,
    onManageCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        item(key = "amount") { AmountHeader(state) }

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

        item(key = "account") {
            PickerSection(
                header = stringResource(R.string.field_account),
                options = state.accounts.map { it.id to it.name },
                selectedId = state.selectedAccountId,
                onSelect = viewModel::selectAccount,
            )
        }

        item(key = "category") {
            if (state.categories.isEmpty()) {
                // Nothing is seeded, so an empty list is the normal first-run state: the
                // way out of it is creating a category, not staring at an empty box.
                CentsListSection(
                    header = stringResource(R.string.field_category),
                    footer = stringResource(R.string.field_category_empty_footer),
                ) {
                    CentsListRow(
                        title = stringResource(R.string.field_category_empty),
                        showChevron = true,
                        showSeparator = false,
                        onClick = onManageCategories,
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
}

@Composable
private fun AmountHeader(state: AddTransactionUiState) {
    Text(
        text = MoneyFormatter.format(state.amount),
        style = CentsTheme.typography.largeTitle,
        color =
            if (state.kind == TransactionKind.INCOME) {
                CentsTheme.colors.positive
            } else {
                CentsTheme.colors.label
            },
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
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

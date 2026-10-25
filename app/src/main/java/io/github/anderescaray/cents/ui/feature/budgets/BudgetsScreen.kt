package io.github.anderescaray.cents.ui.feature.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.usecase.BudgetProgress
import io.github.anderescaray.cents.domain.usecase.BudgetStatus
import io.github.anderescaray.cents.ui.components.BudgetBar
import io.github.anderescaray.cents.ui.components.CategoryBadge
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.formatLocale
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    onNewBudget: () -> Unit,
    onEditBudget: (Long) -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BudgetsContent(state = state, onBack = onBack, onNewBudget = onNewBudget, onEditBudget = onEditBudget)
}

/** The screen without its view model, so a test can draw it from a state. */
@Composable
internal fun BudgetsContent(
    state: BudgetsUiState,
    onBack: () -> Unit,
    onNewBudget: () -> Unit,
    onEditBudget: (Long) -> Unit,
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.budgets_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.tab_reports), onClick = onBack) },
        trailing = { if (state.canAdd) AddBudgetButton(onClick = onNewBudget) },
    ) {
        if (state.isLoading) return@CentsLargeTitleScaffold

        if (state.isEmpty) {
            emptyState(hasExpenseCategories = state.hasExpenseCategories)
        } else {
            budgetRows(state = state, onEditBudget = onEditBudget)
        }
    }
}

private fun LazyListScope.emptyState(hasExpenseCategories: Boolean) {
    item(key = "empty") {
        CentsListSection(
            footer =
                stringResource(
                    if (hasExpenseCategories) R.string.budgets_empty_message else R.string.budgets_empty_no_categories,
                ),
        ) {
            CentsListRow(title = stringResource(R.string.budgets_empty_title), showSeparator = false)
        }
    }
}

private fun LazyListScope.budgetRows(
    state: BudgetsUiState,
    onEditBudget: (Long) -> Unit,
) {
    item(key = "budgets") {
        CentsListSection(
            header = monthLabel(state.month),
            footer = stringResource(R.string.budgets_footer),
        ) {
            state.rows.forEach { row ->
                BudgetRow(row = row, onClick = { onEditBudget(row.budget.id) })
            }
        }
    }
}

/**
 * One category against its limit: what is left (or how far over) where a row puts its value, what
 * made it up underneath, and a bar that says the same thing at a glance, in the colour of D26.
 */
@Composable
internal fun BudgetRow(
    row: BudgetProgress,
    onClick: () -> Unit,
) {
    val tint = row.status.tint()
    Column(modifier = Modifier.testTag(BUDGET_ROW_TAG)) {
        CentsListRow(
            title = row.category.name,
            subtitle = row.detail(),
            value =
                if (row.status == BudgetStatus.OVER) {
                    stringResource(R.string.budget_row_over, MoneyFormatter.format(-row.remaining))
                } else {
                    stringResource(R.string.budget_row_left, MoneyFormatter.format(row.remaining))
                },
            valueColor = if (row.status == BudgetStatus.UNDER) CentsTheme.colors.label else tint,
            leading = {
                CategoryBadge(name = row.category.name, colorArgb = row.category.colorArgb, emoji = row.category.emoji)
            },
            showChevron = true,
            showSeparator = false,
            onClick = onClick,
        )
        BudgetBar(
            fraction = row.fraction,
            color = row.status.fill(),
            trackColor = CentsTheme.colors.fill,
            markColor = CentsTheme.colors.tertiaryLabel,
            modifier =
                Modifier
                    .padding(
                        start = CentsDimens.rowHorizontalPadding,
                        end = CentsDimens.rowHorizontalPadding,
                        bottom = CentsDimens.spacingL,
                    ).fillMaxWidth()
                    .height(BAR_HEIGHT.dp),
        )
    }
}

@Composable
private fun BudgetProgress.detail(): String {
    val spentText = MoneyFormatter.format(spent)
    val limitText = MoneyFormatter.format(budget.limit)
    return when {
        committed.isZero -> {
            stringResource(R.string.budget_row_spent, spentText, limitText)
        }

        committedIncludesEstimates -> {
            stringResource(
                R.string.budget_row_spent_and_estimated,
                spentText,
                MoneyFormatter.format(committed),
                limitText,
            )
        }

        else -> {
            stringResource(R.string.budget_row_spent_and_coming, spentText, MoneyFormatter.format(committed), limitText)
        }
    }
}

/** The bar's fill: blue with room, amber from 80 %, red past the limit (D26). */
@Composable
private fun BudgetStatus.fill(): Color =
    when (this) {
        BudgetStatus.UNDER -> CentsTheme.colors.accent
        BudgetStatus.NEAR -> CentsTheme.colors.warning
        BudgetStatus.OVER -> CentsTheme.colors.destructive
    }

@Composable
private fun BudgetStatus.tint(): Color =
    when (this) {
        BudgetStatus.UNDER -> CentsTheme.colors.accentText
        BudgetStatus.NEAR -> CentsTheme.colors.warningText
        BudgetStatus.OVER -> CentsTheme.colors.destructiveText
    }

@Composable
private fun AddBudgetButton(onClick: () -> Unit) {
    val label = stringResource(R.string.budgets_add)
    RoundIconButton(
        iconRes = R.drawable.ic_plus,
        contentDescription = label,
        onClick = onClick,
        modifier = Modifier.testTag(ADD_BUDGET_TAG),
    )
}

/** "September 2026", written the phone's way, which is what a section header reads as. */
internal fun monthLabel(month: YearMonth): String {
    val locale = formatLocale()
    return month.format(DateTimeFormatter.ofPattern(MONTH_PATTERN, locale)).replaceFirstChar { it.titlecase(locale) }
}

private const val BAR_HEIGHT = 10
private const val MONTH_PATTERN = "LLLL yyyy"
internal const val ADD_BUDGET_TAG = "addBudget"
internal const val BUDGET_ROW_TAG = "budgetRow"

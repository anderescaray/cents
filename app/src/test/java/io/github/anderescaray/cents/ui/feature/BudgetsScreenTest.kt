package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.anderescaray.cents.domain.model.Budget
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.usecase.BudgetProgress
import io.github.anderescaray.cents.ui.feature.budgets.BudgetsContent
import io.github.anderescaray.cents.ui.feature.budgets.BudgetsUiState
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/** What the Budgets screen draws for the general budget beside a category's. */
@RunWith(RobolectricTestRunner::class)
class BudgetsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the general budget is named for all spending, above the categories`() {
        val groceries =
            Category(
                id = 1,
                name = "Groceries",
                kind = CategoryKind.EXPENSE,
                colorArgb = 0,
                parentId = null,
                archived = false,
                sortOrder = 0,
            )
        val state =
            BudgetsUiState(
                rows =
                    listOf(
                        progress(Budget(2, null, Money(150_000, "EUR")), category = null, spent = 25_000),
                        progress(Budget(1, groceries.id, Money(40_000, "EUR")), category = groceries, spent = 10_000),
                    ),
                month = YearMonth.of(2026, 9),
                isLoading = false,
            )

        composeRule.setContent {
            CentsTheme { BudgetsContent(state = state, onBack = {}, onNewBudget = {}, onEditBudget = {}) }
        }

        val general = composeRule.onNodeWithText("All spending").fetchSemanticsNode().boundsInRoot
        val category = composeRule.onNodeWithText("Groceries").fetchSemanticsNode().boundsInRoot
        assertTrue(general.top < category.top)
    }

    private fun progress(
        budget: Budget,
        category: Category?,
        spent: Long,
    ) = BudgetProgress(
        budget = budget,
        category = category,
        spent = Money(spent, "EUR"),
        committed = Money.zero("EUR"),
        committedIncludesEstimates = false,
    )
}

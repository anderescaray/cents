package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.MonthlyFlow
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.ui.feature.reports.PeriodPreset
import io.github.anderescaray.cents.ui.feature.reports.ReportsScreen
import io.github.anderescaray.cents.ui.feature.reports.ReportsUiState
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the Reports tab actually puts on screen.
 *
 * The view model tests prove the right numbers are worked out; these prove they are the ones
 * shown, and that the three screens that are *not* a chart — no accounts, an empty period,
 * and a custom range — say something rather than rendering blank.
 */
@RunWith(RobolectricTestRunner::class)
class ReportsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `each category is shown with its share and its amount`() {
        setContent(stateWithSpending())

        composeRule.onNodeWithText("Rent").assertExists()
        composeRule.onNodeWithText("Groceries").assertExists()
        // Precomputed shares, rendered as whole percents: 750 of 1000 is 75%.
        composeRule.onNodeWithText("75%").assertExists()
        composeRule.onNodeWithText("25%").assertExists()
    }

    @Test
    fun `spending without a category is named rather than left blank`() {
        setContent(
            stateWithSpending(
                slices =
                    listOf(
                        slice("Rent", 750, 0.75f, categoryId = 1),
                        slice(name = null, minor = 250, share = 0.25f, categoryId = null),
                    ),
            ),
        )

        // A row with no title would be a silent gap in a chart that claims to add to 100%.
        composeRule.onNodeWithText("Uncategorized").assertExists()
    }

    @Test
    fun `tapping a category hands back the slice it belongs to`() {
        var opened: CategorySpending? = null
        setContent(stateWithSpending(), onDrillDown = { opened = it })

        scrollTo("Groceries")
        composeRule.onNodeWithText("Groceries").assertHasClickAction().performClick()

        assertEquals("Groceries", opened?.name)
    }

    @Test
    fun `an empty period says so instead of drawing an empty ring`() {
        setContent(
            ReportsUiState(
                isLoading = false,
                spending = SpendingBreakdown(AUGUST, emptyList(), Money.zero("EUR")),
                monthlyFlow = emptyList(),
            ),
        )

        composeRule.onNodeWithText("Nothing was spent in this period.").assertExists()
        scrollTo("No movements in this period.")
        composeRule.onNodeWithText("No movements in this period.").assertExists()
    }

    @Test
    fun `having no accounts is its own screen, not an empty chart`() {
        setContent(ReportsUiState(isLoading = false, hasNoAccounts = true))

        // "Nothing happened yet" and "you have not started yet" are different answers, and
        // only one of them tells the owner what to do next.
        composeRule.onNodeWithText("No accounts yet").assertExists()
        composeRule.onNodeWithText("Nothing was spent in this period.").assertDoesNotExist()
    }

    @Test
    fun `a preset period shows no date rows`() {
        setContent(stateWithSpending())

        composeRule.onNodeWithText("From").assertDoesNotExist()
    }

    @Test
    fun `a custom period shows both ends of the range`() {
        setContent(
            stateWithSpending().copy(
                preset = PeriodPreset.CUSTOM,
                period = ReportPeriod.Custom(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
            ),
        )

        composeRule.onNodeWithText("From").assertExists()
        composeRule.onNodeWithText("To").assertExists()
        composeRule.onNodeWithText("2026-08-31").assertExists()
    }

    @Test
    fun `every period is offered, and choosing one is reported`() {
        var chosen: PeriodPreset? = null
        setContent(stateWithSpending(), onSelectPreset = { chosen = it })

        composeRule.onNodeWithText("Week").assertExists()
        composeRule.onNodeWithText("Quarter").assertExists()
        composeRule.onNodeWithText("Custom").assertExists()
        composeRule.onNodeWithText("Year").performClick()

        assertEquals(PeriodPreset.YEAR, chosen)
    }

    /**
     * The tab is a `LazyColumn`, so anything below the fold is not composed until it is
     * scrolled to — and a tap on a composed-but-offscreen row lands outside the window and
     * never arrives. Both are test mechanics rather than anything the screen does wrong.
     */
    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun setContent(
        state: ReportsUiState,
        onSelectPreset: (PeriodPreset) -> Unit = {},
        onDrillDown: (CategorySpending) -> Unit = {},
    ) {
        composeRule.setContent {
            CentsTheme {
                ReportsScreen(
                    state = state,
                    onSelectPreset = onSelectPreset,
                    onSetRange = { _, _ -> },
                    onDrillDown = onDrillDown,
                )
            }
        }
    }

    private fun stateWithSpending(
        slices: List<CategorySpending> =
            listOf(
                slice("Rent", 750, 0.75f, categoryId = 1),
                slice("Groceries", 250, 0.25f, categoryId = 2),
            ),
    ) = ReportsUiState(
        isLoading = false,
        period = AUGUST,
        spending = SpendingBreakdown(AUGUST, slices, Money(slices.sumOf { it.total.minorUnits }, "EUR")),
        monthlyFlow =
            listOf(
                MonthlyFlow(
                    YearMonth.of(2026, 8),
                    PeriodSummary(Money(200_000, "EUR"), Money(1_000, "EUR")),
                ),
            ),
    )

    private fun slice(
        name: String?,
        minor: Long,
        share: Float,
        categoryId: Long?,
    ) = CategorySpending(
        categoryId = categoryId,
        name = name,
        colorArgb = null,
        total = Money(minor, "EUR"),
        transactionCount = 1,
        share = share,
    )

    private companion object {
        val AUGUST = ReportPeriod.Month(YearMonth.of(2026, 8))
    }
}

package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.anderescaray.cents.domain.model.BalanceOverTime
import io.github.anderescaray.cents.domain.model.BalancePoint
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.MonthlyFlow
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.ui.feature.reports.PeriodPreset
import io.github.anderescaray.cents.ui.feature.reports.ReportsScreen
import io.github.anderescaray.cents.ui.feature.reports.ReportsUiState
import io.github.anderescaray.cents.ui.feature.reports.section.BalanceOverTimeSection
import io.github.anderescaray.cents.ui.feature.reports.section.MonthlyFlowSection
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingSection
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
 * shown, and that the screens that are *not* a chart — no accounts, an empty period, a custom
 * range — say something rather than rendering blank.
 *
 * Every test here renders the stateless overload. That is deliberate and load-bearing: a
 * screen that needed a view model would need Hilt and a database to be looked at, and the
 * whole tab is testable on the JVM precisely because a section draws itself from a state
 * object anyone can build by hand.
 */
@RunWith(RobolectricTestRunner::class)
class ReportsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `each category is shown with its share and its amount`() {
        setContent(fullState())

        composeRule.onNodeWithText("Rent").assertExists()
        composeRule.onNodeWithText("Groceries").assertExists()
        // Precomputed shares, rendered as whole percents: 750 of 1000 is 75%.
        composeRule.onNodeWithText("75%").assertExists()
        composeRule.onNodeWithText("25%").assertExists()
    }

    @Test
    fun `spending without a category is named rather than left blank`() {
        setContent(
            fullState(
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
    fun `tapping a category hands back the filter for the movements behind it`() {
        var opened: TransactionFilter? = null
        setContent(fullState(), onDrillDown = { opened = it })

        scrollTo("Groceries")
        composeRule.onNodeWithText("Groceries").assertHasClickAction().performClick()

        // The screen hands over a filter, not a slice: what was tapped is the section's
        // business, and that is what lets a later view drill down on something else.
        assertEquals(CategoryFilter.One(GROCERIES_ID), opened?.category)
        assertEquals(LocalDate.of(2026, 8, 1), opened?.from)
        assertEquals(LocalDate.of(2026, 8, 31), opened?.to)
    }

    @Test
    fun `the balance line shows where the period started, ended and what it changed`() {
        setContent(fullState())

        scrollTo("BALANCE OVER TIME")
        composeRule.onNodeWithText("Started with").assertExists()
        composeRule.onNodeWithText("Ended with").assertExists()
        // The chart is drawn against an axis that does not start at zero, so the level it is
        // drawn at has to be said in words as well.
        composeRule.onNodeWithText("Change").assertExists()
    }

    @Test
    fun `an empty period says so instead of drawing an empty ring or a flat line`() {
        setContent(
            ReportsUiState(
                isLoading = false,
                period = AUGUST,
                sections =
                    listOf(
                        SpendingSection(SpendingBreakdown(AUGUST, emptyList(), Money.zero("EUR"))),
                        MonthlyFlowSection(emptyList()),
                        BalanceOverTimeSection(
                            BalanceOverTime(AUGUST, Money(50_000, "EUR"), emptyList(), activeDays = 0),
                        ),
                    ),
            ),
        )

        composeRule.onNodeWithText("Nothing was spent in this period.").assertExists()
        scrollTo("No movements in this period.")
        composeRule.onNodeWithText("No movements in this period.").assertExists()
        scrollTo("Nothing moved in this period.")
        composeRule.onNodeWithText("Nothing moved in this period.").assertExists()
    }

    @Test
    fun `having no accounts is its own screen, not an empty chart`() {
        setContent(ReportsUiState(isLoading = false, hasNoAccounts = true))

        // "Nothing happened yet" and "you have not started yet" are different answers, and
        // only one of them tells the owner what to do next.
        composeRule.onNodeWithText("No accounts yet").assertExists()
        composeRule.onNodeWithText("Nothing was spent in this period.").assertDoesNotExist()
        composeRule.onNodeWithText("Nothing moved in this period.").assertDoesNotExist()
    }

    @Test
    fun `the screen renders whatever sections it is given, and only those`() {
        // The seam, asserted from the outside: one section in, one section on screen, and
        // nothing in this file had to name it for that to work.
        setContent(
            ReportsUiState(
                isLoading = false,
                period = AUGUST,
                sections = listOf(MonthlyFlowSection(months())),
            ),
        )

        // Section headers are uppercased by CentsListSection, which is what an iOS grouped
        // list does; the assertion matches what is on screen rather than what was passed in.
        composeRule.onNodeWithText("INCOME AND EXPENSES").assertExists()
        composeRule.onNodeWithText("WHERE THE MONEY WENT").assertDoesNotExist()
        composeRule.onNodeWithText("BALANCE OVER TIME").assertDoesNotExist()
    }

    @Test
    fun `a preset period shows no date rows`() {
        setContent(fullState())

        composeRule.onNodeWithText("From").assertDoesNotExist()
    }

    @Test
    fun `a custom period shows both ends of the range`() {
        setContent(
            fullState().copy(
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
        setContent(fullState(), onSelectPreset = { chosen = it })

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
     *
     * It matches on the scroll-to-index action rather than on "can scroll": the two cartesian
     * charts are scrollable themselves, so the looser matcher now finds three nodes and picks
     * none. Only the list can be asked for an index.
     */
    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    private fun setContent(
        state: ReportsUiState,
        onSelectPreset: (PeriodPreset) -> Unit = {},
        onDrillDown: (TransactionFilter) -> Unit = {},
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

    private fun fullState(
        slices: List<CategorySpending> =
            listOf(
                slice("Rent", 750, 0.75f, categoryId = 1),
                slice("Groceries", 250, 0.25f, categoryId = GROCERIES_ID),
            ),
    ) = ReportsUiState(
        isLoading = false,
        period = AUGUST,
        sections =
            listOf(
                SpendingSection(
                    SpendingBreakdown(AUGUST, slices, Money(slices.sumOf { it.total.minorUnits }, "EUR")),
                ),
                MonthlyFlowSection(months()),
                BalanceOverTimeSection(
                    BalanceOverTime(
                        period = AUGUST,
                        opening = Money(100_000, "EUR"),
                        points =
                            listOf(
                                BalancePoint(LocalDate.of(2026, 8, 1), Money(100_000, "EUR")),
                                BalancePoint(LocalDate.of(2026, 8, 2), Money(140_000, "EUR")),
                            ),
                        activeDays = 1,
                    ),
                ),
            ),
    )

    private fun months() =
        listOf(
            MonthlyFlow(
                YearMonth.of(2026, 8),
                PeriodSummary(Money(200_000, "EUR"), Money(1_000, "EUR")),
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
        const val GROCERIES_ID = 2L
    }
}

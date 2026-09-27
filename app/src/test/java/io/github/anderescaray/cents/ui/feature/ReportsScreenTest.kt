package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.BalanceOverTime
import io.github.anderescaray.cents.domain.model.BalancePoint
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.CategoryTrend
import io.github.anderescaray.cents.domain.model.ComparedRanges
import io.github.anderescaray.cents.domain.model.ComparisonSegment
import io.github.anderescaray.cents.domain.model.FlowBucket
import io.github.anderescaray.cents.domain.model.FlowBucketSize
import io.github.anderescaray.cents.domain.model.FlowShape
import io.github.anderescaray.cents.domain.model.IncomeAndExpenses
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodComparison
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.model.SpendingByWeekday
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.TrendBucket
import io.github.anderescaray.cents.domain.model.TrendChoice
import io.github.anderescaray.cents.domain.model.WeekdaySpending
import io.github.anderescaray.cents.ui.feature.reports.PeriodPreset
import io.github.anderescaray.cents.ui.feature.reports.ReportsScreen
import io.github.anderescaray.cents.ui.feature.reports.ReportsUiState
import io.github.anderescaray.cents.ui.feature.reports.section.BalanceOverTimeSection
import io.github.anderescaray.cents.ui.feature.reports.section.CategoryTrendSection
import io.github.anderescaray.cents.ui.feature.reports.section.KIND_CONTROL_TAG
import io.github.anderescaray.cents.ui.feature.reports.section.MonthlyFlowSection
import io.github.anderescaray.cents.ui.feature.reports.section.PeriodComparisonSection
import io.github.anderescaray.cents.ui.feature.reports.section.SEGMENT_CONTROL_TAG
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingByWeekdaySection
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingSection
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
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

    /** Which categories the trend section was pointed at while a test ran. */
    private val watched = mutableListOf<Long>()

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
    fun `the ring answers where the money came from when income is asked for`() {
        setContent(fullState())

        // Spending is what the tab opens on (D4), so income is a question that has to be
        // asked before it is answered.
        composeRule.onNodeWithText("WHERE THE MONEY WENT").assertExists()

        selectKind("Income")

        composeRule.onNodeWithText("WHERE THE MONEY CAME FROM").assertExists()
        composeRule.onNodeWithText("Total earned").assertExists()
        composeRule.onNodeWithText("Salary").assertExists()
        // The two rings are separate answers, not one list re-sorted: what was spent on rent
        // has no business showing under where the money came from.
        composeRule.onNodeWithText("Rent").assertDoesNotExist()
    }

    @Test
    fun `a slice of the income ring opens income, not the expenses filed under it`() {
        var opened: TransactionFilter? = null
        setContent(fullState(), onDrillDown = { opened = it })

        selectKind("Income")
        scrollTo("Salary")
        composeRule.onNodeWithText("Salary").performClick()

        // The kind travels with the filter. Without it the list would answer with every
        // movement in that category, and a ring that adds up to 20,00 would open a list
        // that does not.
        assertEquals(TransactionKind.INCOME, opened?.kind)
        assertEquals(CategoryFilter.One(SALARY_ID), opened?.category)
    }

    @Test
    fun `a period with no income says so on the income side alone`() {
        setContent(fullState(incomeSlices = emptyList()))

        selectKind("Income")

        composeRule.onNodeWithText("Nothing came in during this period.").assertExists()

        selectKind("Expense")

        // Switching back is not a reload: the expenses were never taken off the state.
        composeRule.onNodeWithText("Rent").assertExists()
    }

    @Test
    fun `one account is not a choice, so no picker is drawn`() {
        setContent(fullState(accounts = listOf(account(CURRENT_ID, "Current"))))

        // A row offering a single answer says what the screen already said.
        composeRule.onNodeWithText("All accounts").assertDoesNotExist()
    }

    @Test
    fun `two accounts are offered, along with the way back to all of them`() {
        setContent(fullState(accounts = twoAccounts()))

        composeRule.onNodeWithText("All accounts").assertExists()
        composeRule.onNodeWithText("Current").assertExists()
        composeRule.onNodeWithText("Savings").assertExists()
    }

    @Test
    fun `choosing an account is reported, and so is choosing all of them again`() {
        val chosen = mutableListOf<Long?>()
        setContent(
            fullState(accounts = twoAccounts(), accountId = CURRENT_ID),
            onSelectAccount = { chosen += it },
        )

        composeRule.onNodeWithText("Savings").performClick()
        composeRule.onNodeWithText("All accounts").performClick()

        // Null is a real answer rather than the absence of one: it is how the tab goes back
        // to every account, and it has to be reachable from the same list.
        assertEquals(listOf(SAVINGS_ID, null), chosen)
    }

    @Test
    fun `an archived account is offered, and says that it is archived`() {
        setContent(
            fullState(accounts = twoAccounts() + account(OLD_ID, "Old card", archived = true)),
        )

        // The charts have always counted an archived account's movements. Leaving it out
        // here would make its history unreachable from the tab that exists to look at it.
        composeRule.onNodeWithText("Old card (archived)").assertExists()
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
    fun `the weekday view lists each day and answers whether weekends cost more`() {
        setContent(fullState())

        scrollTo("SPENDING BY WEEKDAY")
        composeRule.onNodeWithText("Saturday").assertExists()
        composeRule.onNodeWithText("Monday").assertExists()
        // Saturday is 9 000 against a flat 1 000 on the weekdays, so a typical weekend day
        // costs more — the answer said in words under the bars.
        composeRule
            .onNodeWithText("A typical weekend day costs you more than a typical weekday.")
            .assertExists()
    }

    @Test
    fun `tapping a weekday hands back the filter for that day's movements`() {
        var opened: TransactionFilter? = null
        setContent(fullState(), onDrillDown = { opened = it })

        scrollTo("Saturday")
        composeRule.onNodeWithText("Saturday").assertHasClickAction().performClick()

        // The weekday is what a spending-by-weekday bar drills down by — new to the filter
        // for this view, and shown on the Filters form so it can be cleared (D10).
        assertEquals(DayOfWeek.SATURDAY, opened?.weekday)
        assertEquals(TransactionKind.EXPENSE, opened?.kind)
        assertEquals(LocalDate.of(2026, 8, 1), opened?.from)
    }

    @Test
    fun `the comparison names each measure and the way back to the previous period`() {
        setContent(fullState())

        scrollTo("THIS PERIOD AGAINST THE LAST")
        // "Expenses" is on screen twice on purpose: once labelling the pair of columns and
        // once naming the row that says what they add up to. The rows say the change in
        // words, because two bars of similar height do not tell anyone whether they saved
        // forty euros or four hundred.
        composeRule.onAllNodesWithText("Expenses").assertCountEquals(2)
        // "Previous period" is on the segmented control and in the legend.
        composeRule.onAllNodesWithText("Previous period").assertCountEquals(2)
        composeRule.onNodeWithText("Movements in the previous period").assertExists()
    }

    @Test
    fun `the comparison answers this period against last year when that segment is selected`() {
        setContent(fullState())

        scrollTo("THIS PERIOD AGAINST THE LAST")
        composeRule.onNodeWithText("THIS PERIOD AGAINST THE LAST").assertExists()

        selectSegment("Same period last year")

        composeRule.onNodeWithText("THIS PERIOD AGAINST LAST YEAR").assertExists()
        composeRule.onAllNodesWithText("Same period last year").assertCountEquals(2)
        composeRule.onNodeWithText("Movements in the same period last year").assertExists()
    }

    @Test
    fun `the latest column is described under the chart, and opens its movements`() {
        var opened: TransactionFilter? = null
        setContent(fullState(accounts = twoAccounts(), accountId = CURRENT_ID), onDrillDown = { opened = it })

        // August is the latest column, so its figures are the ones shown until another is
        // tapped: 2 000 € in, 10 € out, 1 990 € saved.
        scrollTo("August 2026")
        composeRule.onNodeWithText("August 2026").assertExists()
        composeRule.onNodeWithText("July 2026").assertDoesNotExist()

        scrollTo("Show movements")
        composeRule.onNode(hasTestTag(MonthlyFlowSection.OPEN_TAG)).performClick()

        // The column's own dates, inside the account the tab is narrowed to, and every kind:
        // the column is income against expenses, so it opens both.
        assertEquals(LocalDate.of(2026, 8, 1), opened?.from)
        assertEquals(LocalDate.of(2026, 8, 31), opened?.to)
        assertEquals(CURRENT_ID, opened?.accountId)
        assertEquals(null, opened?.kind)
    }

    @Test
    fun `a quiet latest column is not the default, and has nothing to open`() {
        // A year read in August: September to December are still to come, and empty.
        val withAQuietEnd =
            flow().copy(
                buckets =
                    flow().buckets + bucket(YearMonth.of(2026, 9), income = 0, expenses = 0),
            )
        setContent(
            ReportsUiState(isLoading = false, period = AUGUST, sections = listOf(MonthlyFlowSection(withAQuietEnd) {})),
        )

        composeRule.onNodeWithText("August 2026").assertExists()
        composeRule.onNodeWithText("September 2026").assertDoesNotExist()
        composeRule.onNode(hasTestTag(MonthlyFlowSection.OPEN_TAG)).assertExists()
    }

    @Test
    fun `what was left is said in words, and overspending is not a negative saving`() {
        val overspent =
            flow().copy(
                buckets =
                    listOf(bucket(YearMonth.of(2026, 8), income = 10_000, expenses = 18_510)),
            )
        setContent(
            ReportsUiState(isLoading = false, period = AUGUST, sections = listOf(MonthlyFlowSection(overspent) {})),
        )

        composeRule.onNodeWithText("Overspent").assertExists()
    }

    @Test
    fun `choosing quarters or a count asks the source for other columns`() {
        val asked = mutableListOf<FlowShape>()
        setContent(
            ReportsUiState(
                isLoading = false,
                period = AUGUST,
                sections = listOf(MonthlyFlowSection(flow()) { asked += it }),
            ),
        )

        selectIn(MonthlyFlowSection.SIZE_TAG, "Quarters")
        selectIn(MonthlyFlowSection.COUNT_TAG, "24")

        // Each choice is reported on the shape the state carries. A real source would then
        // replace the state; this fake one does not, so the count arrives with months.
        assertEquals(
            listOf(FlowShape(FlowBucketSize.QUARTER), FlowShape(FlowBucketSize.MONTH, count = 24)),
            asked,
        )
    }

    @Test
    fun `the previous period's row opens the movements behind it`() {
        var opened: TransactionFilter? = null
        setContent(fullState(), onDrillDown = { opened = it })

        scrollTo("Movements in the previous period")
        composeRule.onNodeWithText("Movements in the previous period").performClick()

        // July, not August: it is the one stretch on this tab no other view can reach.
        assertEquals(LocalDate.of(2026, 7, 1), opened?.from)
        assertEquals(LocalDate.of(2026, 7, 31), opened?.to)
    }

    @Test
    fun `the year-ago row opens the movements from the same period last year`() {
        var opened: TransactionFilter? = null
        setContent(fullState(), onDrillDown = { opened = it })

        scrollTo("THIS PERIOD AGAINST THE LAST")
        selectSegment("Same period last year")

        scrollTo("Movements in the same period last year")
        composeRule.onNodeWithText("Movements in the same period last year").performClick()

        // August 2025, not July 2026: the year-ago segment opens that period's movements.
        assertEquals(LocalDate.of(2025, 8, 1), opened?.from)
        assertEquals(LocalDate.of(2025, 8, 31), opened?.to)
    }

    @Test
    fun `the trend names the category it is following and offers the others`() {
        setContent(fullState())

        scrollTo("Tap to follow a different category")
        composeRule.onNodeWithText("Tap to follow a different category").assertExists()
        // The other categories are behind a tap rather than listed under every chart: there
        // can be as many as the owner has typed.
        composeRule.onNodeWithText("Holidays").assertDoesNotExist()

        composeRule.onNodeWithText("Tap to follow a different category").performClick()
        scrollTo("Holidays")

        composeRule.onNodeWithText("Holidays").assertExists()
    }

    @Test
    fun `choosing another category is reported to the section that owns the choice`() {
        setContent(fullState())

        scrollTo("Tap to follow a different category")
        composeRule.onNodeWithText("Tap to follow a different category").performClick()
        scrollTo("Holidays")
        composeRule.onNodeWithText("Holidays").performClick()

        // Which category is watched is the view's own business — it does not travel through
        // ReportActions, and nothing else on the tab has an opinion about it.
        assertEquals(listOf(SALARY_ID), watched)
    }

    @Test
    fun `two empty periods say there is nothing to compare`() {
        setContent(
            ReportsUiState(
                isLoading = false,
                period = AUGUST,
                sections = listOf(PeriodComparisonSection(comparison(current = 0, before = 0))),
            ),
        )

        composeRule.onNodeWithText("Nothing happened in either period.").assertExists()
    }

    @Test
    fun `an empty period says so instead of drawing an empty ring or a flat line`() {
        setContent(
            ReportsUiState(
                isLoading = false,
                period = AUGUST,
                sections =
                    listOf(
                        SpendingSection(
                            expense = SpendingBreakdown(scopeFor(), emptyList(), Money.zero("EUR")),
                            income =
                                SpendingBreakdown(
                                    scopeFor(),
                                    emptyList(),
                                    Money.zero("EUR"),
                                    TransactionKind.INCOME,
                                ),
                        ),
                        MonthlyFlowSection(flow().copy(buckets = emptyList())) {},
                        BalanceOverTimeSection(
                            BalanceOverTime(AUGUST, Money(50_000, "EUR"), emptyList(), activeDays = 0),
                        ),
                        PeriodComparisonSection(comparison(current = 0, before = 0)),
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
                sections = listOf(MonthlyFlowSection(flow()) {}),
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

    /**
     * Taps one segment of the ring's direction control.
     *
     * Scoped to the control rather than found by its text: "Income" is also the flow chart's
     * legend, and asking the whole tree for it would be asking which section composed first.
     */
    private fun selectKind(label: String) {
        composeRule
            .onNode(hasTestTag(KIND_CONTROL_TAG))
            .onChildren()
            .filterToOne(hasText(label))
            .performClick()
    }

    /** Taps one segment of whichever control carries [tag]. */
    private fun selectIn(
        tag: String,
        label: String,
    ) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
        composeRule
            .onNode(hasTestTag(tag))
            .onChildren()
            .filterToOne(hasText(label))
            .performClick()
    }

    /**
     * Taps one segment of the comparison section's period selector.
     */
    private fun selectSegment(label: String) {
        composeRule
            .onNode(hasTestTag(SEGMENT_CONTROL_TAG))
            .onChildren()
            .filterToOne(hasText(label))
            .performClick()
    }

    private fun setContent(
        state: ReportsUiState,
        onSelectPreset: (PeriodPreset) -> Unit = {},
        onSelectAccount: (Long?) -> Unit = {},
        onDrillDown: (TransactionFilter) -> Unit = {},
    ) {
        composeRule.setContent {
            CentsTheme {
                ReportsScreen(
                    state = state,
                    onSelectPreset = onSelectPreset,
                    onSelectAccount = onSelectAccount,
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
        incomeSlices: List<CategorySpending> = listOf(slice("Salary", 2_000, 1f, categoryId = SALARY_ID)),
        accounts: List<Account> = emptyList(),
        accountId: Long? = null,
    ) = ReportsUiState(
        isLoading = false,
        period = AUGUST,
        accounts = accounts,
        accountId = accountId,
        sections =
            listOf(
                SpendingSection(
                    expense =
                        SpendingBreakdown(
                            scopeFor(accountId),
                            slices,
                            Money(slices.sumOf { it.total.minorUnits }, "EUR"),
                        ),
                    income =
                        SpendingBreakdown(
                            scopeFor(accountId),
                            incomeSlices,
                            Money(incomeSlices.sumOf { it.total.minorUnits }, "EUR"),
                            TransactionKind.INCOME,
                        ),
                ),
                MonthlyFlowSection(flow(accountId)) {},
                PeriodComparisonSection(
                    previous = comparison(current = 90_000, before = 120_000),
                    samePeriodLastYear =
                        comparison(
                            current = 90_000,
                            before = 110_000,
                            targetPeriod = AUGUST_LAST_YEAR,
                            segment = ComparisonSegment.SAME_PERIOD_LAST_YEAR,
                        ),
                ),
                CategoryTrendSection(trend(), onSelectCategory = { watched += it }),
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
                // Last in the fake state rather than in tab order: it sits between the flow
                // and comparison sections on the real screen, and putting it there here would
                // change which of their look-alike rows a `LazyColumn` keeps composed.
                SpendingByWeekdaySection(weekdaySpending()),
            ),
    )

    private fun trend(selected: String = "Restaurants") =
        CategoryTrend(
            scope = scopeFor(),
            selected = TrendChoice(GROCERIES_ID, selected, 0),
            choices =
                listOf(
                    TrendChoice(GROCERIES_ID, selected, 0),
                    TrendChoice(SALARY_ID, "Holidays", 0),
                ),
            buckets =
                listOf(
                    TrendBucket(JULY, JULY, Money(10_000, "EUR")),
                    TrendBucket(AUGUST, AUGUST, Money(30_000, "EUR")),
                ),
        )

    private fun weekdaySpending() =
        SpendingByWeekday(
            scope = scopeFor(),
            days =
                DayOfWeek.entries.map { day ->
                    // Saturday well above the flat weekday, so the footer verdict is fixed.
                    val minor = if (day == DayOfWeek.SATURDAY) 9_000L else 1_000L
                    WeekdaySpending(day, Money(minor, "EUR"), 1)
                },
        )

    private fun comparison(
        current: Long,
        before: Long,
        targetPeriod: ReportPeriod = JULY,
        segment: ComparisonSegment = ComparisonSegment.PREVIOUS_PERIOD,
    ) = PeriodComparison(
        // A whole August against a whole July (or August last year): the screen tests are
        // about what is drawn, and the clamping a running period gets is the repository's
        // business and tested there.
        scope = scopeFor(),
        ranges = ComparedRanges(current = AUGUST, previous = targetPeriod, partial = false),
        current = PeriodSummary(Money.zero("EUR"), Money(current, "EUR")),
        before = PeriodSummary(Money.zero("EUR"), Money(before, "EUR")),
        segment = segment,
    )

    /** July quiet and August with 2 000 € in and 10 € out, the way the repository hands them over. */
    private fun flow(accountId: Long? = null) =
        IncomeAndExpenses(
            scope = scopeFor(accountId),
            shape = FlowShape(),
            buckets =
                listOf(
                    bucket(YearMonth.of(2026, 7), income = 0, expenses = 0),
                    bucket(YearMonth.of(2026, 8), income = 200_000, expenses = 1_000),
                ),
        )

    private fun bucket(
        month: YearMonth,
        income: Long,
        expenses: Long,
    ) = FlowBucket(
        whole = ReportPeriod.Month(month),
        counted = ReportPeriod.Month(month),
        summary = PeriodSummary(Money(income, "EUR"), Money(expenses, "EUR")),
    )

    /**
     * The scope the fake sections report on. It carries the account the state says the tab is
     * narrowed to, because that is what a drill-down reads — a state claiming one account
     * while its slices carry a filter for all of them would test nothing the app does.
     */
    private fun scopeFor(accountId: Long? = null) = ReportScope(AUGUST, "EUR", accountId)

    private fun twoAccounts() = listOf(account(CURRENT_ID, "Current"), account(SAVINGS_ID, "Savings"))

    private fun account(
        id: Long,
        name: String,
        archived: Boolean = false,
    ) = Account(
        id = id,
        name = name,
        type = AccountType.BANK,
        currency = "EUR",
        openingBalance = Money.zero("EUR"),
        colorArgb = 0,
        archived = archived,
        sortOrder = 0,
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
        val JULY = ReportPeriod.Month(YearMonth.of(2026, 7))
        val AUGUST_LAST_YEAR = ReportPeriod.Month(YearMonth.of(2025, 8))
        const val GROCERIES_ID = 2L
        const val SALARY_ID = 3L
        const val CURRENT_ID = 10L
        const val SAVINGS_ID = 11L
        const val OLD_ID = 12L
    }
}

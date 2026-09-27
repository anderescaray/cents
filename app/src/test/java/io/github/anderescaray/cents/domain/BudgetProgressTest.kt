package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.Budget
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Postponement
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.BudgetStatus
import io.github.anderescaray.cents.domain.usecase.budgetProgressOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The arithmetic of a budget (PRD §4.4, `DECISIONS.md` D26): what went out, what is known to go
 * out, and how close the two come to the limit.
 */
class BudgetProgressTest {
    private val today = LocalDate.of(2026, 9, 23)
    private val groceries = category(1, "Groceries", sortOrder = 1)
    private val home = category(2, "Home", sortOrder = 0)

    @Test
    fun `what was spent counts against the limit`() {
        val row = progress(limit = 40_000, spent = 12_000).single()

        assertEquals(Money(12_000, "EUR"), row.used)
        assertEquals(Money(28_000, "EUR"), row.remaining)
        assertEquals(BudgetStatus.UNDER, row.status)
        assertEquals(0.3f, row.fraction, 0.0001f)
    }

    @Test
    fun `a subscription still due this month counts from day one`() {
        // The internet bill falls on the 25th: not charged yet, but no longer a surprise.
        val internet = rule(categoryId = groceries.id, minor = 5_000, start = LocalDate.of(2026, 9, 25))

        val row = progress(limit = 40_000, spent = 12_000, rules = listOf(internet)).single()

        assertEquals(Money(5_000, "EUR"), row.committed)
        assertEquals(Money(17_000, "EUR"), row.used)
        assertFalse(row.committedIncludesEstimates)
    }

    @Test
    fun `a charge already written this month is counted once, as spent`() {
        val rent =
            rule(
                categoryId = groceries.id,
                minor = 5_000,
                start = LocalDate.of(2026, 9, 1),
                lastGenerated = LocalDate.of(2026, 9, 1),
            )

        val row = progress(limit = 40_000, spent = 5_000, rules = listOf(rent)).single()

        assertTrue(row.committed.isZero)
        assertEquals(Money(5_000, "EUR"), row.used)
    }

    @Test
    fun `a variable bill still waiting counts at its estimate and says so`() {
        val electricity =
            rule(categoryId = groceries.id, minor = 7_000, start = LocalDate.of(2026, 9, 5), mode = RuleMode.CONFIRM)

        val row = progress(limit = 40_000, spent = 0, rules = listOf(electricity)).single()

        assertEquals(Money(7_000, "EUR"), row.committed)
        assertTrue(row.committedIncludesEstimates)
    }

    @Test
    fun `last month's unanswered bill is not this month's budget`() {
        // August's bill will be recorded on 5 August when it is answered, so only September's
        // counts against September — the Subscriptions screen still counts both, as money owed.
        val electricity =
            rule(categoryId = groceries.id, minor = 7_000, start = LocalDate.of(2026, 8, 5), mode = RuleMode.CONFIRM)

        val row = progress(limit = 40_000, spent = 0, rules = listOf(electricity)).single()

        assertEquals(Money(7_000, "EUR"), row.committed)
    }

    @Test
    fun `a charge moved into next month leaves this month's budget, and one moved in joins it`() {
        // Due on the 30th, moved to 2 October: October's books, not September's.
        val rent =
            rule(
                categoryId = groceries.id,
                minor = 5_000,
                start = LocalDate.of(2026, 8, 30),
                lastGenerated = LocalDate.of(2026, 8, 30),
            )
        // August's bill, moved to the 25th: it will be dated in September, and it is still ahead.
        val water =
            rule(
                categoryId = groceries.id,
                minor = 3_000,
                start = LocalDate.of(2026, 7, 31),
                lastGenerated = LocalDate.of(2026, 7, 31),
            )
        val moved =
            listOf(
                Postponement(rent.id, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2)),
                Postponement(water.id, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 25)),
            )

        val row =
            budgetProgressOf(
                budgets = listOf(Budget(1, groceries.id, Money(40_000, "EUR"))),
                categories = listOf(groceries, home),
                spent = emptyMap(),
                rules = listOf(rent, water),
                today = today,
                postponements = moved,
            ).single()

        // Water's own September occurrence (the 30th) and its moved August one; no rent.
        assertEquals(Money(6_000, "EUR"), row.committed)
    }

    @Test
    fun `last month's variable bill moved into this month is this month's budget`() {
        // Due 31 August, moved to 3 September and still waiting: it will be recorded on the 3rd,
        // so September counts it, beside September's own on the 30th.
        val electricity =
            rule(categoryId = groceries.id, minor = 7_000, start = LocalDate.of(2026, 8, 31), mode = RuleMode.CONFIRM)

        val row =
            budgetProgressOf(
                budgets = listOf(Budget(1, groceries.id, Money(40_000, "EUR"))),
                categories = listOf(groceries, home),
                spent = emptyMap(),
                rules = listOf(electricity),
                today = today,
                postponements =
                    listOf(Postponement(electricity.id, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 3))),
            ).single()

        assertEquals(Money(14_000, "EUR"), row.committed)
        assertTrue(row.committedIncludesEstimates)
    }

    @Test
    fun `other categories, income and stopped rules are not this budget's business`() {
        val rules =
            listOf(
                rule(categoryId = home.id, minor = 80_000, start = LocalDate.of(2026, 9, 30)),
                rule(
                    categoryId = groceries.id,
                    minor = 1_000,
                    start = LocalDate.of(2026, 9, 30),
                    kind = TransactionKind.INCOME,
                ),
                rule(categoryId = groceries.id, minor = 2_000, start = LocalDate.of(2026, 9, 30), active = false),
            )

        assertTrue(progress(limit = 40_000, spent = 0, rules = rules).single().committed.isZero)
    }

    @Test
    fun `the warning starts at eighty percent, and exactly the limit is not over`() {
        assertEquals(BudgetStatus.UNDER, progress(limit = 10_000, spent = 7_999).single().status)
        assertEquals(BudgetStatus.NEAR, progress(limit = 10_000, spent = 8_000).single().status)
        assertEquals(BudgetStatus.NEAR, progress(limit = 10_000, spent = 10_000).single().status)
        assertEquals(BudgetStatus.OVER, progress(limit = 10_000, spent = 10_001).single().status)
    }

    @Test
    fun `over the limit is a full bar and a negative remainder`() {
        val row = progress(limit = 10_000, spent = 13_000).single()

        assertEquals(1f, row.fraction, 0f)
        assertEquals(Money(-3_000, "EUR"), row.remaining)
    }

    @Test
    fun `an archived category keeps its budget out of sight`() {
        val archived = groceries.copy(archived = true)

        val rows =
            budgetProgressOf(
                budgets = listOf(Budget(1, groceries.id, Money(40_000, "EUR"))),
                categories = listOf(archived, home),
                spent = emptyMap(),
                rules = emptyList(),
                today = today,
                postponements = emptyList(),
            )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `budgets are listed in the order the categories are`() {
        val rows =
            budgetProgressOf(
                budgets =
                    listOf(
                        Budget(1, groceries.id, Money(40_000, "EUR")),
                        Budget(2, home.id, Money(90_000, "EUR")),
                    ),
                categories = listOf(groceries, home),
                spent = emptyMap(),
                rules = emptyList(),
                today = today,
                postponements = emptyList(),
            )

        assertEquals(listOf("Home", "Groceries"), rows.map { it.category.name })
    }

    private fun progress(
        limit: Long,
        spent: Long,
        rules: List<RecurringRule> = emptyList(),
    ) = budgetProgressOf(
        budgets = listOf(Budget(1, groceries.id, Money(limit, "EUR"))),
        categories = listOf(groceries, home),
        spent = mapOf(groceries.id to Money(spent, "EUR")),
        rules = rules,
        today = today,
        postponements = emptyList(),
    )

    private fun category(
        id: Long,
        name: String,
        sortOrder: Int,
    ) = Category(
        id = id,
        name = name,
        kind = CategoryKind.EXPENSE,
        colorArgb = 0,
        parentId = null,
        archived = false,
        sortOrder = sortOrder,
    )

    private fun rule(
        categoryId: Long,
        minor: Long,
        start: LocalDate,
        mode: RuleMode = RuleMode.AUTOMATIC,
        kind: TransactionKind = TransactionKind.EXPENSE,
        active: Boolean = true,
        lastGenerated: LocalDate? = null,
    ) = RecurringRule(
        id = minor,
        name = "Rule",
        accountId = 1,
        categoryId = categoryId,
        kind = kind,
        amount = Money(minor, "EUR"),
        frequency = Frequency.MONTHLY,
        intervalDays = null,
        startDate = start,
        endDate = null,
        mode = mode,
        active = active,
        lastGenerated = lastGenerated,
    )
}

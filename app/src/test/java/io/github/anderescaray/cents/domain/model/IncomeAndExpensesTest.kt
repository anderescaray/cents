package io.github.anderescaray.cents.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Which columns the income-and-expenses chart draws, and what a tap on one opens. */
class IncomeAndExpensesTest {
    @Test
    fun `twelve months end with the month the period ends in`() {
        val buckets = bucketsFor(ReportPeriod.Month(YearMonth.of(2026, 8)), FlowShape())

        assertEquals(12, buckets.size)
        assertEquals(ReportPeriod.Month(YearMonth.of(2025, 9)), buckets.first().first)
        assertEquals(ReportPeriod.Month(YearMonth.of(2026, 8)), buckets.last().first)
        // August is whole, so nothing is cut.
        assertEquals(buckets.last().first, buckets.last().second)
    }

    @Test
    fun `a quarter is the calendar quarter, walked back whole`() {
        val buckets =
            bucketsFor(
                ReportPeriod.Month(YearMonth.of(2026, 8)),
                FlowShape(FlowBucketSize.QUARTER, count = 6),
            )

        assertEquals(6, buckets.size)
        assertEquals(LocalDate.of(2025, 4, 1), buckets.first().first.from)
        assertEquals(LocalDate.of(2025, 6, 30), buckets.first().first.to)
        assertEquals(LocalDate.of(2026, 7, 1), buckets.last().first.from)
        // The last quarter stops where August does: September was not asked about.
        assertEquals(LocalDate.of(2026, 9, 30), buckets.last().first.to)
        assertEquals(LocalDate.of(2026, 8, 31), buckets.last().second.to)
    }

    @Test
    fun `a year's column read from a custom range stops where the range does`() {
        val range = ReportPeriod.Custom(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 5, 20))

        val buckets = bucketsFor(range, FlowShape(FlowBucketSize.YEAR, count = 6))

        assertEquals(ReportPeriod.Year(2021), buckets.first().first)
        assertEquals(ReportPeriod.Custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 20)), buckets.last().second)
    }

    @Test
    fun `a tap opens the part of the column that was counted, in the tab's account`() {
        val scope = ReportScope(ReportPeriod.Month(YearMonth.of(2026, 8)), "EUR", accountId = 7)
        val cut = ReportPeriod.Custom(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31))
        val bucket =
            FlowBucket(
                whole = ReportPeriod.Quarter(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)),
                counted = cut,
                summary = PeriodSummary(Money.zero("EUR"), Money.zero("EUR")),
            )

        val filter = IncomeAndExpenses(scope, FlowShape(), listOf(bucket)).filterFor(bucket)

        assertEquals(LocalDate.of(2026, 7, 1), filter.from)
        assertEquals(LocalDate.of(2026, 8, 31), filter.to)
        assertEquals(7L, filter.accountId)
        assertEquals(null, filter.kind)
    }
}

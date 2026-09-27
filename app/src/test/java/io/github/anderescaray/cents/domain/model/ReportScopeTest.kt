package io.github.anderescaray.cents.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * What a month's row on the income-and-expenses view opens: the month, but never more of it
 * than the column above the row added up.
 */
class ReportScopeTest {
    @Test
    fun `a whole month inside the period opens the whole month`() {
        val scope = ReportScope(ReportPeriod.Year(2026), "EUR", accountId = 7)

        val filter = scope.filterForMonth(YearMonth.of(2026, 2))

        assertEquals(LocalDate.of(2026, 2, 1), filter.from)
        assertEquals(LocalDate.of(2026, 2, 28), filter.to)
        assertEquals(7L, filter.accountId)
        assertEquals(null, filter.kind)
    }

    @Test
    fun `the months at either end of a custom range open only the part inside it`() {
        val scope =
            ReportScope(
                ReportPeriod.Custom(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 9, 10)),
                "EUR",
            )

        val july = scope.filterForMonth(YearMonth.of(2026, 7))
        val september = scope.filterForMonth(YearMonth.of(2026, 9))

        assertEquals(LocalDate.of(2026, 7, 20), july.from)
        assertEquals(LocalDate.of(2026, 7, 31), july.to)
        assertEquals(LocalDate.of(2026, 9, 1), september.from)
        assertEquals(LocalDate.of(2026, 9, 10), september.to)
    }
}

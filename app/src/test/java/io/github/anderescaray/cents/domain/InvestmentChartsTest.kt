package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.ChartPoint
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.usecase.chartPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** An investment chart's points by week, month and year (`DECISIONS.md` D40). */
class InvestmentChartsTest {
    /** Worth 1 000 € until the 15th of October 2026, then 1 100 €, then 1 300 € from November. */
    private val valueOn: (LocalDate) -> Money = { day ->
        when {
            day.isBefore(LocalDate.of(2026, 10, 15)) -> eur(100_000)
            day.isBefore(LocalDate.of(2026, 11, 1)) -> eur(110_000)
            else -> eur(130_000)
        }
    }

    @Test
    fun `a week is read at its Sunday, and this week today`() {
        // Thursday 1 October 2026: its week starts on Monday 28 September.
        val points = chartPoints(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), ChartScale.WEEK, valueOn)

        assertEquals(
            listOf(
                ChartPoint(LocalDate.of(2026, 9, 28), eur(100_000)),
                ChartPoint(LocalDate.of(2026, 10, 5), eur(100_000)),
                ChartPoint(LocalDate.of(2026, 10, 12), eur(100_000)),
            ),
            points,
        )
    }

    @Test
    fun `a week nobody typed in keeps the last value`() {
        val points = chartPoints(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 30), ChartScale.WEEK, valueOn)

        assertEquals(listOf(100_000L, 110_000L, 110_000L, 110_000L), points.map { it.value.minorUnits })
    }

    @Test
    fun `a month is the mean of the weeks that end in it`() {
        // October's weeks end on the 4th, 11th, 18th and 25th: 1 000, 1 000, 1 100, 1 100.
        // The week ending 1 November belongs to November, with today's 1 300 € beside it.
        val points = chartPoints(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 11, 3), ChartScale.MONTH, valueOn)

        assertEquals(
            listOf(
                ChartPoint(LocalDate.of(2026, 10, 1), eur(105_000)),
                ChartPoint(LocalDate.of(2026, 11, 1), eur(130_000)),
            ),
            points,
        )
    }

    @Test
    fun `a year is the mean of its months`() {
        val points = chartPoints(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 11, 3), ChartScale.YEAR, valueOn)

        assertEquals(listOf(ChartPoint(LocalDate.of(2026, 1, 1), eur(117_500))), points)
    }

    @Test
    fun `nothing recorded draws nothing`() {
        assertTrue(chartPoints(null, LocalDate.of(2026, 11, 3), ChartScale.WEEK, valueOn).isEmpty())
    }

    private fun eur(minor: Long) = Money(minor, "EUR")
}

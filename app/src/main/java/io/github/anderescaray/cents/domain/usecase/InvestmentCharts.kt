package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.ChartPoint
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.divideRoundingHalfUp
import java.math.BigInteger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * An investment chart's points, at the [scale] the owner chose (`DECISIONS.md` D40).
 *
 * - A **week** runs Monday to Sunday, and its point is what [valueOn] says at its end, or today
 *   for the week still under way. A week nobody typed a value in keeps the last one, because
 *   [valueOn] carries it forward.
 * - A **month** is the mean of the weeks that end in it (this week counts in this month), and a
 *   **year** the mean of its months. A mean rather than the last value, because the owner asked to
 *   see how much was held over the period, and a month that dipped and recovered should show it.
 *
 * Starts with the week of [first], and is empty when nothing ever happened.
 */
fun chartPoints(
    first: LocalDate?,
    today: LocalDate,
    scale: ChartScale,
    valueOn: (LocalDate) -> Money,
): List<ChartPoint> {
    if (first == null || first.isAfter(today)) return emptyList()
    val weeks = weeklyPoints(first, today, valueOn)
    return when (scale) {
        ChartScale.WEEK -> weeks.map { ChartPoint(it.start, it.value) }
        ChartScale.MONTH -> monthlyPoints(weeks)
        ChartScale.YEAR -> yearlyPoints(monthlyPoints(weeks))
    }
}

/** One week's point, with the day it was read on, which decides the month it belongs to. */
private data class WeekPoint(
    val start: LocalDate,
    val readOn: LocalDate,
    val value: Money,
)

private fun weeklyPoints(
    first: LocalDate,
    today: LocalDate,
    valueOn: (LocalDate) -> Money,
): List<WeekPoint> {
    val points = mutableListOf<WeekPoint>()
    var start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    while (!start.isAfter(today)) {
        val end = start.plusDays(DAYS_IN_WEEK - 1)
        val readOn = if (end.isAfter(today)) today else end
        points += WeekPoint(start, readOn, valueOn(readOn))
        start = start.plusDays(DAYS_IN_WEEK)
    }
    return points
}

private fun monthlyPoints(weeks: List<WeekPoint>): List<ChartPoint> =
    weeks
        .groupBy { it.readOn.withDayOfMonth(1) }
        .map { (month, inMonth) -> ChartPoint(month, meanOf(inMonth.map { it.value })) }

private fun yearlyPoints(months: List<ChartPoint>): List<ChartPoint> =
    months
        .groupBy { it.start.withDayOfYear(1) }
        .map { (year, inYear) -> ChartPoint(year, meanOf(inYear.map { it.value })) }

/** The mean, rounded half up to the cent. Never called on an empty list. */
private fun meanOf(values: List<Money>): Money {
    val sum = values.fold(BigInteger.ZERO) { total, value -> total + BigInteger.valueOf(value.minorUnits) }
    return Money(divideRoundingHalfUp(sum, BigInteger.valueOf(values.size.toLong())), values.first().currency)
}

private const val DAYS_IN_WEEK = 7L

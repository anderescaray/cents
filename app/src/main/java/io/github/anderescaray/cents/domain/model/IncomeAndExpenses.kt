package io.github.anderescaray.cents.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** How long one column of the income-and-expenses chart is. */
enum class FlowBucketSize {
    MONTH,
    QUARTER,
    YEAR,
    ;

    /** The whole month, quarter or year [date] falls in. */
    fun periodContaining(date: LocalDate): ReportPeriod =
        when (this) {
            MONTH -> {
                ReportPeriod.Month(YearMonth.from(date))
            }

            QUARTER -> {
                val start =
                    LocalDate.of(
                        date.year,
                        (date.monthValue - 1) / MONTHS_IN_QUARTER * MONTHS_IN_QUARTER + 1,
                        1,
                    )
                ReportPeriod.Quarter(start, start.plusMonths(MONTHS_IN_QUARTER.toLong()).minusDays(1))
            }

            YEAR -> {
                ReportPeriod.Year(date.year)
            }
        }

    private companion object {
        const val MONTHS_IN_QUARTER = 3
    }
}

/**
 * What the chart is asked to draw: columns of [size], and [count] of them.
 *
 * Chosen on the chart itself rather than taken from the period control, because the
 * question is a different one. The period says *which stretch*; this says *how finely to cut
 * the time around it*, and "the last twelve months" is worth asking with a month selected.
 */
data class FlowShape(
    val size: FlowBucketSize = FlowBucketSize.MONTH,
    val count: Int = DEFAULT_COUNT,
) {
    companion object {
        const val DEFAULT_COUNT = 12

        /** The counts offered on the chart. */
        val COUNTS = listOf(6, DEFAULT_COUNT, 24)
    }
}

/**
 * One column: a month, a quarter or a year.
 *
 * [whole] is what the axis names. [counted] is the part that was summed, and is shorter only
 * for the last column, which stops where the selected period does. A tap opens [counted], so a
 * column never opens movements it did not add up.
 */
data class FlowBucket(
    val whole: ReportPeriod,
    val counted: ReportPeriod,
    val summary: PeriodSummary,
) {
    val isEmpty: Boolean get() = summary.income.isZero && summary.expenses.isZero
}

/**
 * Income against expenses, over the last [FlowShape.count] months, quarters or years.
 *
 * **Like the category trend, this view reads outside the selected period, on purpose.** How
 * much was saved month by month is a question about several months, and a month selected at
 * the top would otherwise draw one column. The period still anchors it: the last column is the
 * one the period ends in, cut where the period ends, and the rest run back from there.
 *
 * Every column is present, the quiet ones as zeros: a gap in a bar chart reads as missing data,
 * and a zero reads as a quiet month.
 */
data class IncomeAndExpenses(
    val scope: ReportScope,
    val shape: FlowShape,
    val buckets: List<FlowBucket>,
) {
    val isEmpty: Boolean get() = buckets.all { it.isEmpty }

    /** The movements behind one column: its own dates, in whatever account the tab is narrowed to. */
    fun filterFor(bucket: FlowBucket): TransactionFilter = scope.copy(period = bucket.counted).asFilter()
}

/**
 * The columns [shape] asks for, oldest first, ending with the one [period] ends in.
 *
 * The last one is cut to end where [period] does: a year's column read with August selected
 * stops at the end of August, because the months after it are not what was asked about.
 */
fun bucketsFor(
    period: ReportPeriod,
    shape: FlowShape,
): List<Pair<ReportPeriod, ReportPeriod>> {
    val wholes = shape.size.periodContaining(period.to).lastBuckets(shape.count)
    return wholes.mapIndexed { index, whole ->
        val counted =
            if (index == wholes.lastIndex && whole.to > period.to) {
                ReportPeriod.Custom(whole.from, period.to)
            } else {
                whole
            }
        whole to counted
    }
}

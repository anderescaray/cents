package io.github.anderescaray.cents.domain.model

import java.time.temporal.ChronoUnit

/**
 * This period against the one before it.
 *
 * The question is "am I doing better or worse than last time", and the only honest answer is
 * a comparison against a stretch of the **same shape**: a month against a month, a quarter
 * against a quarter. Comparing August against "the last thirty days" would silently overlap
 * itself, and comparing it against July's first eighteen days would flatter it.
 *
 * [previous] is that stretch, worked out by [ReportPeriod.previous]. It is carried here
 * rather than recomputed on screen so that the number shown and the movements a tap opens
 * are the same range, from the same place.
 */
data class PeriodComparison(
    val scope: ReportScope,
    val previous: ReportPeriod,
    val current: PeriodSummary,
    val before: PeriodSummary,
) {
    /**
     * True when neither period holds anything.
     *
     * A comparison of two empty periods is not a chart with two zero columns — it is a
     * question with no answer, and the section says so in words. One empty period is a real
     * answer, though: "you spent nothing last month" is exactly what someone is looking for.
     */
    val isEmpty: Boolean
        get() =
            current.income.isZero && current.expenses.isZero &&
                before.income.isZero && before.expenses.isZero

    val incomeChange: Money get() = current.income - before.income

    val expensesChange: Money get() = current.expenses - before.expenses

    val netChange: Money get() = current.net - before.net

    /** The movements behind either column, as a filter the transaction list already applies. */
    fun filterForCurrent(): TransactionFilter = scope.asFilter()

    fun filterForPrevious(): TransactionFilter = scope.copy(period = previous).asFilter()
}

/**
 * The stretch of the same shape immediately before this one.
 *
 * Every case is contiguous with the period it came from — the previous month ends the day
 * before this month starts — so no case has to reason about lengths except [ReportPeriod
 * .Custom], which has no shape of its own and takes "the same number of days, ending the day
 * before".
 *
 * A calendar shape is kept as that shape rather than flattened into two dates: the previous
 * month of February is January, whatever their lengths, and subtracting 28 days would produce
 * a range that is neither.
 */
fun ReportPeriod.previous(): ReportPeriod =
    when (this) {
        is ReportPeriod.Week -> {
            ReportPeriod.Week(from.minusWeeks(1), to.minusWeeks(1))
        }

        is ReportPeriod.Month -> {
            ReportPeriod.Month(month.minusMonths(1))
        }

        is ReportPeriod.Quarter -> {
            ReportPeriod.Quarter(from.minusMonths(MONTHS_IN_QUARTER), from.minusDays(1))
        }

        is ReportPeriod.Year -> {
            ReportPeriod.Year(year - 1)
        }

        is ReportPeriod.Custom -> {
            val days = ChronoUnit.DAYS.between(from, to) + 1
            ReportPeriod.Custom(from.minusDays(days), from.minusDays(1))
        }
    }

private const val MONTHS_IN_QUARTER = 3L

package io.github.anderescaray.cents.domain.model

import java.time.LocalDate
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
    val ranges: ComparedRanges,
    val current: PeriodSummary,
    val before: PeriodSummary,
) {
    val previous: ReportPeriod get() = ranges.previous

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

    /**
     * The movements behind either column, as a filter the transaction list already applies.
     *
     * Built from [ranges] rather than from the scope's period, or a tap would open a month
     * of movements under a column that only added up three days of it.
     */
    fun filterForCurrent(): TransactionFilter = scope.copy(period = ranges.current).asFilter()

    fun filterForPrevious(): TransactionFilter = scope.copy(period = ranges.previous).asFilter()
}

/**
 * The two stretches actually compared, which are not always the two whole periods.
 *
 * **A period that has not finished yet is compared against the same part of the one before
 * it.** The tab opens on "this month", and `periodFor` means the whole calendar month by
 * that — so on the third of September the selected period is 1–30 September while only
 * three days of it have happened. Comparing that against the whole of August says spending
 * fell by 95%, on the third of the month, for about twenty-seven days out of every thirty.
 * It is the same flattery [ReportPeriod.previous] exists to avoid, arrived at from the other
 * side, and the fix is the same principle: compare like with like.
 *
 * [partial] is true when that clamping happened, so the section can say why the dates it
 * shows are not the dates in the selector. A period that has already ended is compared
 * whole, and nothing is clamped.
 */
data class ComparedRanges(
    val current: ReportPeriod,
    val previous: ReportPeriod,
    val partial: Boolean,
)

/**
 * What to compare this period against, given what day it is.
 *
 * The elapsed part is counted in days and taken off the *start* of the previous period, not
 * its end: "the first three days of September against the first three days of August" is the
 * comparison someone means, and it is the one that stays true as the month fills up.
 */
fun ReportPeriod.comparedWith(today: LocalDate): ComparedRanges {
    val whole = ComparedRanges(current = this, previous = previous(), partial = false)
    // `> to`, not `>= to`: on the last day of a period the day is not over, and February's
    // twenty-eight days against the whole of January is the same flattery in miniature.
    if (today < from || today > to) return whole

    val elapsedDays = ChronoUnit.DAYS.between(from, today)
    val before = previous()
    return ComparedRanges(
        current = ReportPeriod.Custom(from, today),
        previous = ReportPeriod.Custom(before.from, minOf(before.to, before.from.plusDays(elapsedDays))),
        partial = true,
    )
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

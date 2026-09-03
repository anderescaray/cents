package io.github.anderescaray.cents.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * The stretch of time a report is about.
 *
 * Half of what every view on the Reports tab is asked about; the other half is which account,
 * and [ReportScope] is the two of them together. Each view's own model — [SpendingBreakdown],
 * [MonthlyFlow], [BalanceOverTime] — lives in a file of its own beside this one, so a new view
 * is a new file rather than one more edit to a growing shared one.
 *
 * A period is a [TransactionFilter] date range with a name, not a second idea of "a
 * period". It does **not** know how to turn itself into a filter, though: it used to, and
 * that method became a way to build a drill-down that carried the dates and silently
 * dropped the account. [ReportScope.asFilter] is the one way, because it is the only thing
 * that knows everything a report was narrowed by.
 */
sealed interface ReportPeriod {
    val from: LocalDate
    val to: LocalDate

    data class Week(
        override val from: LocalDate,
        override val to: LocalDate,
    ) : ReportPeriod

    data class Month(
        val month: YearMonth,
    ) : ReportPeriod {
        override val from: LocalDate get() = month.atDay(1)
        override val to: LocalDate get() = month.atEndOfMonth()
    }

    data class Quarter(
        override val from: LocalDate,
        override val to: LocalDate,
    ) : ReportPeriod

    data class Year(
        val year: Int,
    ) : ReportPeriod {
        override val from: LocalDate get() = LocalDate.of(year, 1, 1)
        override val to: LocalDate get() = LocalDate.of(year, 12, 31)
    }

    /** Whatever two dates the owner picked. */
    data class Custom(
        override val from: LocalDate,
        override val to: LocalDate,
    ) : ReportPeriod
}

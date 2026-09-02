package io.github.anderescaray.cents.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * The stretch of time a report is about.
 *
 * This is the only thing every view on the Reports tab shares. Each view's own model —
 * [SpendingBreakdown], [MonthlyFlow], [BalanceOverTime] — lives in a file of its own beside
 * this one, so a new view is a new file rather than one more edit to a growing shared one.
 *
 * A period is a [TransactionFilter] date range with a name, not a second idea of "a
 * period": [asFilter] is what a drill-down hands to the transaction list, so tapping a
 * slice of a chart lands on exactly the movements the slice was made of.
 */
sealed interface ReportPeriod {
    val from: LocalDate
    val to: LocalDate

    /** The same range the transaction list already knows how to apply. */
    fun asFilter(): TransactionFilter = TransactionFilter(from = from, to = to)

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

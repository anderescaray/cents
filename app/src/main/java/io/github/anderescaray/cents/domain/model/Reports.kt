package io.github.anderescaray.cents.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * The stretch of time a report is about.
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

/**
 * What one category accounted for over a period.
 *
 * [categoryId] is null for the uncategorized group — money that was spent without a label.
 * It is deliberately a slice rather than a footnote: leaving it out would make the slices
 * add up to less than the total with nothing on screen explaining the difference.
 *
 * [share] is precomputed here rather than in a composable, so the chart does no arithmetic
 * while it draws (`CLAUDE.md` §5).
 */
data class CategorySpending(
    val categoryId: Long?,
    val name: String?,
    val colorArgb: Int?,
    val total: Money,
    val transactionCount: Int,
    val share: Float,
)

/**
 * A period's spending, largest slice first, with the total it was divided from.
 *
 * [isEmpty] is a real state and not an error: a month with no expenses is a perfectly good
 * answer to "where did my money go", and the chart has to say so in words rather than
 * drawing an empty circle.
 */
data class SpendingBreakdown(
    val period: ReportPeriod,
    val slices: List<CategorySpending>,
    val total: Money,
    /** Which direction the slices are, so a drill-down asks for the same movements. */
    val kind: TransactionKind = TransactionKind.EXPENSE,
) {
    val isEmpty: Boolean get() = slices.isEmpty()

    /**
     * The movements one slice is made of, expressed as a filter.
     *
     * All three parts matter and each one is a way the list could disagree with the chart:
     * the **period**, or the list would show every month; the **category**, which is the
     * slice itself and is [CategoryFilter.Uncategorized] rather than "any" for the unnamed
     * one; and the **kind**, or an income movement filed under Groceries would appear in a
     * list that claims to add up to an expense slice.
     */
    fun filterFor(slice: CategorySpending): TransactionFilter =
        period.asFilter().copy(
            category =
                slice.categoryId?.let(CategoryFilter::One) ?: CategoryFilter.Uncategorized,
            kind = kind,
        )
}

/**
 * One month of the income-versus-expenses chart.
 *
 * Every month in the range is present, including the ones with nothing in them — a gap in
 * a bar chart reads as missing data, while a zero reads as a quiet month, and only one of
 * those is true.
 */
data class MonthlyFlow(
    val month: YearMonth,
    val summary: PeriodSummary,
) {
    val isEmpty: Boolean get() = summary.income.isZero && summary.expenses.isZero
}

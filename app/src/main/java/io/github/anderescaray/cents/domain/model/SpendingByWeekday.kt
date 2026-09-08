package io.github.anderescaray.cents.domain.model

import java.time.DayOfWeek

/** What was spent on one day of the week over the period. */
data class WeekdaySpending(
    val weekday: DayOfWeek,
    val total: Money,
    val transactionCount: Int,
)

/**
 * Do weekends wreck me: what was spent on each day of the week, as seven bars.
 *
 * [days] is always the full week, Monday first and in that order, with the quiet days present
 * as zeros — a missing bar reads as missing data, and a zero reads as a day nothing was spent.
 * The order here is fixed so it can be tested; which day the chart *starts* on is the UI's
 * call, made from the device locale.
 *
 * Expenses only, and deliberately so. The ring one section up already answers "where did the
 * money come from"; this view's whole question is about spending, and "do weekends wreck me"
 * is not asked of income. A view ships when it answers one question properly (D4).
 */
data class SpendingByWeekday(
    val scope: ReportScope,
    val days: List<WeekdaySpending>,
    val kind: TransactionKind = TransactionKind.EXPENSE,
) {
    val isEmpty: Boolean get() = days.all { it.total.isZero }

    val total: Money
        get() = days.map { it.total }.reduceOrNull(Money::plus) ?: Money.zero(scope.currency)

    /**
     * Saturday and Sunday against the other five — the comparison the view exists to make.
     *
     * The weekend is Saturday and Sunday flatly, not read from the locale: the app ships in
     * English and Spanish, both of which mean that by it, and `WeekFields` has no portable
     * notion of which days are the weekend anyway. A locale that disagrees would have to
     * revisit this, the same way a right-to-left translation would have to revisit the swipe.
     */
    val weekendTotal: Money
        get() =
            days.filter { it.weekday in WEEKEND }.map { it.total }.reduceOrNull(Money::plus)
                ?: Money.zero(scope.currency)

    val weekdayTotal: Money
        get() =
            days.filterNot { it.weekday in WEEKEND }.map { it.total }.reduceOrNull(Money::plus)
                ?: Money.zero(scope.currency)

    fun on(weekday: DayOfWeek): WeekdaySpending =
        days.firstOrNull { it.weekday == weekday }
            ?: WeekdaySpending(weekday, Money.zero(scope.currency), 0)

    /**
     * The movements behind one bar, expressed as a filter.
     *
     * The period and the account come from the scope, as with every drill-down on the tab, so
     * a bar tapped while one account is chosen opens that account's movements. The weekday is
     * the bar itself — a concept `TransactionFilter` gained for exactly this (D10), so the
     * Filters form can show and clear it once the list is narrowed.
     */
    fun filterFor(weekday: DayOfWeek): TransactionFilter = scope.asFilter().copy(weekday = weekday, kind = kind)

    companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

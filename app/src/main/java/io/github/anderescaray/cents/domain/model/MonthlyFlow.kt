package io.github.anderescaray.cents.domain.model

import java.time.YearMonth

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

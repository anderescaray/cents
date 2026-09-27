package io.github.anderescaray.cents.domain.model

import java.time.YearMonth

/**
 * What a report is about: a stretch of time, and whose money.
 *
 * Every view on the Reports tab used to be asked two things — a [ReportPeriod] and a
 * currency. An account is a third, and it is not the kind of thing that can be added to one
 * view: a tab where the ring is narrowed to a current account while the balance line still
 * counts every account is a screen that contradicts itself. So the two parameters became one
 * object, and a view that ignores [accountId] is a view that has not been finished rather
 * than a view that opted out.
 *
 * [accountId] null means **every account**, which is what the tab opens on. It is the same
 * spelling [TransactionFilter.accountId] uses, so the two never have to be translated.
 *
 * **[asFilter] is the only way a report turns into a list.** It used to live on
 * [ReportPeriod], which was correct until an account existed and then quietly wrong: a filter
 * built from the period alone carries the dates and drops the account, so a slice tapped
 * while one account was selected would open every account's movements. Building it from the
 * scope is what makes that impossible to write by accident.
 */
data class ReportScope(
    val period: ReportPeriod,
    val currency: String,
    val accountId: Long? = null,
) {
    /** The same filter the transaction list already knows how to apply. */
    fun asFilter(): TransactionFilter =
        TransactionFilter(
            from = period.from,
            to = period.to,
            accountId = accountId,
        )

    /**
     * One calendar month of this scope, as the list should open it.
     *
     * Clamped to the period at both ends, because a custom range from the 20th touches a month
     * it only covers part of, and the column above the row adds up only that part. Opening the
     * whole month would put movements under it that the column never counted.
     */
    fun filterForMonth(month: YearMonth): TransactionFilter =
        asFilter().copy(
            from = maxOf(period.from, month.atDay(1)),
            to = minOf(period.to, month.atEndOfMonth()),
        )
}

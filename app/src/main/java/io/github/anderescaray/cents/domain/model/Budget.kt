package io.github.anderescaray.cents.domain.model

/**
 * A monthly limit on what one expense category may take (PRD §4.4, `DECISIONS.md` D26), or, with
 * no [categoryId], on everything spent in the month: the general budget (the owner, 2026-10-31).
 */
data class Budget(
    val id: Long,
    val categoryId: Long?,
    val limit: Money,
) {
    val isGeneral: Boolean get() = categoryId == null

    companion object {
        const val NEW = 0L
    }
}

/** What a month's spending comes to, for budgets: by expense category, and in all. */
data class BudgetSpending(
    /** By category id. A category with nothing spent is absent. */
    val byCategory: Map<Long, Money>,
    /** Every expense, uncategorized included: what the general budget counts. */
    val total: Money,
)

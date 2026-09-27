package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.Budget
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Postponement
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.LocalDate

/** How close a category is to its limit. */
enum class BudgetStatus {
    /** Under [BudgetProgress.WARNING_PERCENT] of the limit. */
    UNDER,

    /** At or past the warning line, and not over. */
    NEAR,

    /** More than the limit. Exactly the limit is still [NEAR]: it is spent, not overspent. */
    OVER,
}

/**
 * One budget, measured against the month it is in.
 *
 * [spent] is what has gone out of the category this month. [committed] is what is **known** to
 * go out before the month ends: its subscriptions still due, and its variable bills still waiting,
 * at their estimate. PRD §4.4 counts that from day one, because a budget that only learns about
 * the rent on the day it is charged flatters every first week of the month.
 */
data class BudgetProgress(
    val budget: Budget,
    val category: Category,
    val spent: Money,
    val committed: Money,
    val committedIncludesEstimates: Boolean,
) {
    val used: Money get() = spent + committed

    /** What is left, or how far over when negative. */
    val remaining: Money get() = budget.limit - used

    val status: BudgetStatus
        get() =
            when {
                used.minorUnits > budget.limit.minorUnits -> {
                    BudgetStatus.OVER
                }

                // Compared as integers: the percentage is a boundary, not a number to round.
                used.minorUnits * PERCENT >= budget.limit.minorUnits * WARNING_PERCENT -> {
                    BudgetStatus.NEAR
                }

                else -> {
                    BudgetStatus.UNDER
                }
            }

    /** How much of the bar to fill, from 0 to 1. A budget over its limit is a full bar. */
    val fraction: Float
        get() =
            if (budget.limit.minorUnits <= 0) {
                1f
            } else {
                (used.minorUnits.toDouble() / budget.limit.minorUnits).coerceIn(0.0, 1.0).toFloat()
            }

    companion object {
        const val WARNING_PERCENT = 80L
        private const val PERCENT = 100L
    }
}

/**
 * Every budget whose category can still be seen, in the order the categories are listed.
 *
 * A budget on an archived category is kept and not shown (D26): archiving a category says
 * "stop offering it", and a limit on something no longer offered is noise until it comes back.
 *
 * @param spent what each category has taken this month, by category id. A category with no
 * movements is simply absent, and reads as nothing spent.
 * @param postponements occurrences moved to another day: each counts in the month it was moved
 * to, which is the month its movement is dated in (D28).
 */
fun budgetProgressOf(
    budgets: List<Budget>,
    categories: List<Category>,
    spent: Map<Long, Money>,
    rules: List<RecurringRule>,
    today: LocalDate,
    postponements: List<Postponement>,
): List<BudgetProgress> {
    val byId = categories.associateBy { it.id }
    val moved = postponements.byRule()
    return budgets
        .mapNotNull { budget ->
            val category = byId[budget.categoryId]?.takeUnless { it.archived } ?: return@mapNotNull null
            val currency = budget.limit.currency
            val charges =
                rules.filter {
                    it.active && it.kind == TransactionKind.EXPENSE && it.categoryId == category.id &&
                        it.amount.currency == currency
                }
            val perCharge = charges.map { it to it.committedThisMonth(today, moved[it.id].orEmpty()) }
            val committed = perCharge.fold(Money.zero(currency)) { total, (_, amount) -> total + amount }
            BudgetProgress(
                budget = budget,
                category = category,
                spent = spent[category.id]?.takeIf { it.currency == currency } ?: Money.zero(currency),
                committed = committed,
                committedIncludesEstimates =
                    perCharge.any { (rule, amount) -> rule.mode == RuleMode.CONFIRM && amount.minorUnits > 0 },
            )
        }.sortedWith(compareBy({ it.category.sortOrder }, { it.category.name }))
}

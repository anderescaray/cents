package io.github.anderescaray.cents.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Which category a filter is asking about.
 *
 * A nullable id cannot say this on its own: `categoryId = null` already means "this
 * movement was never classified", so it cannot also mean "any category". [Uncategorized] is
 * the inbox — everything captured but not yet given a label — and it is a real thing to go
 * looking for, not an edge case.
 */
sealed interface CategoryFilter {
    data object All : CategoryFilter

    data object Uncategorized : CategoryFilter

    data class One(
        val id: Long,
    ) : CategoryFilter
}

/**
 * What the transaction list is currently being asked to show.
 *
 * Every field is "unset" by default, and unset means "do not narrow by this". The whole
 * point is that it composes: an account *and* a month *and* a category are one question,
 * not three screens.
 *
 * Amounts are minor units, like everything else in the app (rule 1), and are compared
 * against the stored magnitude — a movement's sign lives in its [TransactionKind], so
 * "between 10 and 50" means the same thing for income and for expenses.
 */
data class TransactionFilter(
    /** Free text, matched against the note, the category name and the account name. */
    val query: String = "",
    val accountId: Long? = null,
    val category: CategoryFilter = CategoryFilter.All,
    val kind: TransactionKind? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    /**
     * One day of the week, matched against each movement's own date.
     *
     * It is the odd one out: a date range narrows to a stretch, this narrows to every
     * Saturday inside whatever stretch is already set. It exists because *spending by weekday*
     * drills down like every other chart on the Reports tab (PRD §4.3), and the list can only
     * ever show what this filter can express — so the concept lives here and the Filters form
     * has a section for it, rather than the bar narrowing the list by something the owner
     * cannot see or clear (`DECISIONS.md` D10, answer B).
     */
    val weekday: DayOfWeek? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
) {
    /**
     * How many things are narrowing the list, so the button can say so. Text is deliberately
     * not counted: it has its own field on screen, and counting it twice would read as a
     * filter the owner cannot find.
     */
    val activeCount: Int
        get() =
            listOf(
                accountId != null,
                category != CategoryFilter.All,
                kind != null,
                from != null || to != null,
                weekday != null,
                minAmountMinor != null || maxAmountMinor != null,
            ).count { it }

    /** True when the list is showing something narrower than everything. */
    val isNarrowed: Boolean get() = activeCount > 0 || query.isNotBlank()

    /**
     * A range that can never match anything, because the floor is above the ceiling. Worth
     * saying out loud on the form: an empty list is otherwise indistinguishable from having
     * no movements in that range at all.
     */
    val amountRangeInverted: Boolean
        get() {
            val min = minAmountMinor ?: return false
            val max = maxAmountMinor ?: return false
            return min > max
        }

    /** Same, for a date range entered backwards. */
    val dateRangeInverted: Boolean
        get() {
            val start = from ?: return false
            val end = to ?: return false
            return start > end
        }

    companion object {
        /** Everything, in date order: what the list shows when nothing has been asked. */
        val None = TransactionFilter()
    }
}

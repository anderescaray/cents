package io.github.anderescaray.cents.domain.model

import java.time.LocalDate

/**
 * An occasion that cuts across categories: a trip, a move, a freelance job (PRD §4.9,
 * `DECISIONS.md` D27).
 *
 * A category says what kind of thing the money went on; a group says what it was for. A movement
 * has at most one, so a group's total is a plain sum and no two groups count the same euro.
 *
 * [startDate] and [endDate] are optional, and only ever a suggestion — see [covers].
 */
data class Group(
    val id: Long,
    val name: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /**
     * Kept, with its movements and totals, but no longer offered for new movements: the June trip
     * that should not come up in December. A movement already in it keeps it.
     */
    val archived: Boolean = false,
) {
    /**
     * Whether [date] falls inside the dates the owner gave this group, so the form can offer it
     * first. A group with no dates covers nothing: it is offered in the list like any other, but
     * never ahead of the rest. One end missing leaves that side open.
     */
    fun covers(date: LocalDate): Boolean {
        if (startDate == null && endDate == null) return false
        return (startDate == null || date >= startDate) && (endDate == null || date <= endDate)
    }

    companion object {
        const val NEW = 0L
    }
}

/**
 * A group and what its movements came to.
 *
 * [income] and [expenses] are both positive, like every amount at rest in this app; [net] is the
 * one signed figure, and its sign is the answer to the question the screen asks: negative, the
 * occasion **cost** that much, positive, it **earned** it. [firstDate] and [lastDate] are the span
 * of what was actually recorded, which is what a trip's dates really were, and are null while it
 * holds nothing.
 */
data class GroupSummary(
    val group: Group,
    val income: Money,
    val expenses: Money,
    val movementCount: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
) {
    val net: Money get() = income - expenses

    val isEmpty: Boolean get() = movementCount == 0
}

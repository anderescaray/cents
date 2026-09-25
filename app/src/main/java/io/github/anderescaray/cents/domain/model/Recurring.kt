package io.github.anderescaray.cents.domain.model

import java.time.LocalDate

/**
 * How often a rule comes round.
 *
 * [EVERY_N_DAYS] is the escape hatch for the ones that fit nothing else, and it carries its
 * number on the rule rather than in the enum — an enum with a value in it is a sealed class
 * wearing a disguise, and Room stores enums by name.
 */
enum class Frequency {
    WEEKLY,
    FORTNIGHTLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
    EVERY_N_DAYS,
}

/**
 * Whether the app records the movement or proposes it.
 *
 * [AUTOMATIC] is for a known amount — Netflix at 12,99 € — and [CONFIRM] for one that varies,
 * like electricity. The difference is not how much it costs but whether the app is entitled to
 * assert a number the owner has not seen (PRD §4.4).
 */
enum class RuleMode {
    AUTOMATIC,
    CONFIRM,
}

/**
 * A nudge about a subscription, a number of days away from the day it comes round.
 *
 * [offsetDays] is signed and reads as **occurrence + offset = the day it arrives**: `-3` is three
 * days before, `+1` the day after, `0` the morning of. One number rather than a count and a
 * direction, because two fields can contradict each other and one cannot.
 *
 * A rule may carry several. *Three days before and again on the day* is a real way to be
 * reminded, and the app has no business deciding that one of them is enough.
 */
data class Reminder(
    val id: Long = 0,
    val offsetDays: Int,
) {
    val isBefore: Boolean get() = offsetDays < 0

    val isOnTheDay: Boolean get() = offsetDays == 0

    /** How far away, as a person would say it — the direction is [isBefore]'s business. */
    val days: Int get() = if (offsetDays < 0) -offsetDays else offsetDays

    /** The same distance, on the near side of the date. */
    fun asBefore(): Reminder = if (offsetDays > 0) copy(offsetDays = -offsetDays) else this
}

/**
 * A reminder that has come round today, and what it is about.
 *
 * [occurrence] is the day the charge falls on, which is the thing the notification is *for* —
 * not today. A reminder three days early says "the rent is on the 1st", and it can only say that
 * because it carries the date rather than the day it happens to be firing.
 */
data class DueReminder(
    val rule: RecurringRule,
    val occurrence: LocalDate,
    /** Counted from [postponedTo] when there is one, since that is the day the money moves. */
    val offsetDays: Int,
    /** The later day this occurrence was moved to, if it was. */
    val postponedTo: LocalDate? = null,
) {
    val reminder: Reminder get() = Reminder(offsetDays = offsetDays)

    /** Whether the owner is being asked for a figure rather than told about one. */
    val asksForAmount: Boolean get() = rule.mode == RuleMode.CONFIRM
}

/**
 * One occurrence of a rule moved to a later day: the rent due on the 1st, paid on the 4th.
 *
 * **The movement is dated [postponedTo], and nothing about the rule changes.** It is one
 * occurrence, not a new schedule, so the next month still falls where it always does — counted
 * from the start date, as every occurrence is. A fixed charge is written on the new day; a
 * variable one stops waiting on the Subscriptions screen until that day, and is recorded on it
 * once answered (`DECISIONS.md` D28).
 */
data class Postponement(
    val ruleId: Long,
    val occurrence: LocalDate,
    val postponedTo: LocalDate,
)

/**
 * One occurrence of a variable-amount rule, waiting for the number only the owner knows.
 *
 * The electricity bill falls due on the 15th and the app has no idea what it says, so instead of
 * asserting one it puts the question here. **A rule in [RuleMode.CONFIRM] produces these rather
 * than movements** — and until one is answered or skipped, nothing about that period is recorded,
 * which is the honest state: an unknown amount is not a zero.
 *
 * Only the **oldest** waiting occurrence of each rule is offered at a time, and [laterCount] says
 * how many are behind it. A rule remembers how far it has got with a single date, so answering
 * February before January would swallow January silently; one at a time is what makes that
 * impossible rather than merely unlikely.
 */
data class PendingCharge(
    val rule: RecurringRule,
    val dueDate: LocalDate,
    /** Further occurrences of the same rule waiting behind this one. */
    val laterCount: Int,
    /** The later day the owner moved it to, which is the day it is recorded on. */
    val postponedTo: LocalDate? = null,
) {
    /** The day the movement will carry: the one it was moved to, or the one it fell due on. */
    val recordOn: LocalDate get() = postponedTo ?: dueDate

    /** What the rule was set up expecting, which is the starting point for the real figure. */
    val estimate: Money get() = rule.amount
}

/**
 * A movement that repeats: a subscription, a salary, the rent.
 *
 * **A rule is a template, not a movement.** Each period it generates a real [Transaction]
 * carrying its [Transaction.ruleId], and those movements are ordinary from that moment on —
 * editable, deletable, and counted by every report. That separation is what rule 8 protects:
 * cancelling Netflix in June must not remove January to May, because every chart of the past
 * would change and the app would stop being worth comparing periods with.
 *
 * [startDate] is the **anchor** as well as the beginning, and that matters more than it looks.
 * Occurrences are counted from it rather than from the last one generated, so a monthly rule
 * starting on the 31st lands on the 28th in February and returns to the 31st in March. Stepping
 * forward from the previous occurrence would have pinned it to the 28th for ever after.
 */
data class RecurringRule(
    val id: Long,
    /** What it is called in a list of subscriptions: "Netflix", "Rent". */
    val name: String,
    val accountId: Long,
    val categoryId: Long?,
    val kind: TransactionKind,
    val amount: Money,
    val frequency: Frequency,
    /** Only read when [frequency] is [Frequency.EVERY_N_DAYS]. */
    val intervalDays: Int?,
    val startDate: LocalDate,
    /** After this, nothing more is generated. Null means it runs until it is stopped. */
    val endDate: LocalDate?,
    val mode: RuleMode,
    /**
     * Whether it is still generating.
     *
     * "Delete a subscription" means setting this to false (PRD §4.4): it leaves the active list
     * and stops generating, and everything it already made stays exactly where it is.
     */
    val active: Boolean,
    /**
     * The last occurrence actually written, so a catch-up knows where to start.
     *
     * Null means nothing has been generated yet, which is not the same as the rule being new —
     * a rule created today with a start date in the past has a backlog waiting for it.
     */
    val lastGenerated: LocalDate?,
) {
    /**
     * What it comes to in a year, in whole cents.
     *
     * **An estimate, and integer arithmetic all the way** — no `Double` touches money, even a
     * derived figure nobody stores (rule 1). A year is taken as 365 days, so a weekly charge
     * comes out as 52.14 of them rather than 52: the honest average, and the one that does not
     * quietly lose a week's worth every year.
     *
     * It is the yearly figure that is computed first and the monthly one derived from it,
     * because an annual subscription is the case where the two disagree most and the year is
     * the number that is actually true.
     */
    val yearlyCost: Money
        get() {
            val minor = amount.minorUnits
            val yearly =
                when (frequency) {
                    Frequency.WEEKLY -> perYearOverDays(minor, days = 7)

                    Frequency.FORTNIGHTLY -> perYearOverDays(minor, days = 14)

                    Frequency.MONTHLY -> minor * MONTHS_PER_YEAR

                    Frequency.QUARTERLY -> minor * QUARTERS_PER_YEAR

                    Frequency.YEARLY -> minor

                    // A rule with no interval cannot come round at all, so it costs nothing
                    // rather than dividing by zero.
                    Frequency.EVERY_N_DAYS -> intervalDays?.let { perYearOverDays(minor, it) } ?: 0L
                }
            return Money(yearly, amount.currency)
        }

    /** What it costs in a month: the total that makes a subscriptions screen worth having. */
    val monthlyCost: Money
        get() = Money(divideRounding(yearlyCost.minorUnits, MONTHS_PER_YEAR), amount.currency)

    private fun perYearOverDays(
        minor: Long,
        days: Int,
    ): Long = if (days < 1) 0L else divideRounding(minor * DAYS_PER_YEAR, days.toLong())

    private companion object {
        const val DAYS_PER_YEAR = 365L
        const val MONTHS_PER_YEAR = 12L
        const val QUARTERS_PER_YEAR = 4L

        /** Half away from zero, which is what rounding means outside a compiler. */
        fun divideRounding(
            numerator: Long,
            denominator: Long,
        ): Long {
            if (denominator == 0L) return 0L
            val sign = if ((numerator < 0) != (denominator < 0)) -1 else 1
            val a = if (numerator < 0) -numerator else numerator
            val b = if (denominator < 0) -denominator else denominator
            return sign * ((a * 2 + b) / (b * 2))
        }
    }
}

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

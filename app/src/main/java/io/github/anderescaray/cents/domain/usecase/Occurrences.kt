package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.RecurringRule
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Every occurrence that has fallen due and not yet been generated, oldest first.
 *
 * Bounded at both ends. It starts after [RecurringRule.lastGenerated] — or at the start date
 * itself, which is why a rule created today with a start date in January has a backlog rather
 * than a single charge — and it stops at [today] or the rule's end date, whichever comes first.
 *
 * An inactive rule produces nothing: "delete a subscription" means it stops generating, and that
 * is enforced here rather than by whoever remembers to check (PRD §4.4).
 */
fun RecurringRule.dueOccurrences(today: LocalDate): List<LocalDate> {
    if (!active) return emptyList()
    val last = endDate?.let { if (it < today) it else today } ?: today
    return generateSequence(0) { it + 1 }
        .map { step -> occurrenceAt(step) }
        // The sequence only ever moves forward, so the first one past the end is the end.
        .takeWhile { date -> date != null && date <= last }
        .filterNotNull()
        .filter { date -> lastGenerated == null || date > lastGenerated }
        .toList()
}

/**
 * When it next comes round after [after], or null if it never does again.
 *
 * What a subscriptions screen shows beside each rule, and what says whether a rule that is
 * technically active has quietly run out of road because its end date has passed.
 */
fun RecurringRule.nextOccurrenceAfter(after: LocalDate): LocalDate? {
    if (!active) return null
    return generateSequence(0) { it + 1 }
        .map { step -> occurrenceAt(step) }
        // **The end date bounds the sequence rather than filtering it.** Asking for the next
        // occurrence after a rule has already finished has no answer, and a filter would look
        // for one for ever — the sequence is infinite, so every stopping condition has to be a
        // `takeWhile`.
        .takeWhile { date -> date != null && (endDate == null || date <= endDate) }
        .filterNotNull()
        .firstOrNull { date -> date > after }
}

/**
 * The first occurrence not yet written, whether it has fallen due or is still to come.
 *
 * What a postponement moves. Null once the rule has stopped or run past its end date.
 */
fun RecurringRule.nextUnwritten(): LocalDate? = nextOccurrenceAfter(lastGenerated ?: startDate.minusDays(1))

/**
 * The days [occurrence] can be moved to, soonest first.
 *
 * Counted from the later of the occurrence and today, so *three days* on a bill already waiting
 * means three days from now rather than a date that has passed. **Never as far as the next
 * occurrence**: moving March past April would have the rule write April first, and one
 * occurrence overtaking another is a schedule nobody can read.
 */
fun RecurringRule.postponeChoices(
    occurrence: LocalDate,
    today: LocalDate,
): List<LocalDate> {
    val from = maxOf(occurrence, today)
    val following = nextOccurrenceAfter(occurrence)
    return POSTPONE_DAYS.map { from.plusDays(it) }.filter { following == null || it < following }
}

/**
 * Whether [occurrence] may be moved to [to].
 *
 * It has to be one of the rule's days, not yet written — a written one is a movement, and its
 * date is edited there — and [to] has to be one of [postponeChoices]' days or anywhere between
 * them: later than the occurrence, later than today, and short of the next one.
 */
fun RecurringRule.canPostpone(
    occurrence: LocalDate,
    to: LocalDate,
    today: LocalDate,
): Boolean {
    val following = nextOccurrenceAfter(occurrence)
    return active &&
        occursOn(occurrence) &&
        (lastGenerated == null || occurrence > lastGenerated) &&
        to > occurrence &&
        to > today &&
        (following == null || to < following)
}

/**
 * The [step]th occurrence, counted from the start date.
 *
 * **Counted from the start date, never from the occurrence before it**, and that single choice
 * is most of what makes the arithmetic correct. A monthly rule beginning on the 31st lands on
 * the 28th in February — `java.time` clamps it — and then returns to the 31st in March, because
 * March is computed as *start plus two months* rather than as *February plus one*. Stepping
 * forward from the previous occurrence would have pinned it to the 28th for the rest of its
 * life, and nobody would notice until a year of rent was a day early. The same reasoning covers
 * the 29th of February: a yearly rule anchored there falls on the 28th in ordinary years and
 * comes back on the 29th when one arrives.
 *
 * Null when the rule cannot come round at all — an *every N days* with no N, or a nonsensical
 * one — which stops a sequence rather than looping on a date that never moves. That is the
 * difference between a rule the owner has half-filled in and an infinite loop.
 */
private fun RecurringRule.occurrenceAt(step: Int): LocalDate? =
    when (frequency) {
        Frequency.WEEKLY -> {
            startDate.plusWeeks(step.toLong())
        }

        Frequency.FORTNIGHTLY -> {
            startDate.plusWeeks(step.toLong() * 2)
        }

        Frequency.MONTHLY -> {
            startDate.plusMonths(step.toLong())
        }

        Frequency.QUARTERLY -> {
            startDate.plusMonths(step.toLong() * MONTHS_PER_QUARTER)
        }

        Frequency.YEARLY -> {
            startDate.plusYears(step.toLong())
        }

        Frequency.EVERY_N_DAYS -> {
            intervalDays
                ?.takeIf { it >= 1 }
                ?.let { startDate.plusDays(step.toLong() * it) }
        }
    }

/**
 * Whether this rule falls on exactly that day.
 *
 * What a reminder needs: firing three days early means asking *is `today + 3` one of this rule's
 * days*, and there is no list to look it up in — the sequence is infinite and the answer is
 * usually about a date in the future.
 *
 * The monthly cases go **through [occurrenceAt] rather than around it**, which is the only part
 * worth reading twice. A rule anchored on the 31st lands on the 28th in February, so "is the 28th
 * one of its days" cannot be answered by comparing day-of-month — it is answered by computing
 * the occurrence for that month and seeing whether it is the same date. One definition of where
 * a rule lands, used by both the generator and the reminders, so the two can never disagree about
 * when the rent is.
 */
fun RecurringRule.occursOn(date: LocalDate): Boolean {
    val withinRange = date >= startDate && (endDate == null || date <= endDate)
    val step = if (withinRange) stepFor(date) else null
    return step != null && step >= 0 && occurrenceAt(step) == date
}

/** Which occurrence [date] would be, if it is one at all. Null when it cannot be. */
private fun RecurringRule.stepFor(date: LocalDate): Int? {
    val days = ChronoUnit.DAYS.between(startDate, date)
    val months = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(date))
    return when (frequency) {
        Frequency.WEEKLY -> {
            days.takeIf { it % DAYS_PER_WEEK == 0L }?.div(DAYS_PER_WEEK)
        }

        Frequency.FORTNIGHTLY -> {
            days.takeIf { it % (DAYS_PER_WEEK * 2) == 0L }?.div(DAYS_PER_WEEK * 2)
        }

        Frequency.MONTHLY -> {
            months
        }

        Frequency.QUARTERLY -> {
            months.takeIf { it % MONTHS_PER_QUARTER == 0L }?.div(MONTHS_PER_QUARTER)
        }

        Frequency.YEARLY -> {
            months.takeIf { it % MONTHS_PER_YEAR == 0L }?.div(MONTHS_PER_YEAR)
        }

        Frequency.EVERY_N_DAYS -> {
            intervalDays
                ?.takeIf { it >= 1 }
                ?.let { interval -> days.takeIf { it % interval == 0L }?.div(interval) }
        }
    }?.toInt()
}

/** A day, a weekend, a week or two: how far a bill or a salary is usually late. */
private val POSTPONE_DAYS = listOf(1L, 2L, 3L, 5L, 7L, 14L)

private const val MONTHS_PER_QUARTER = 3L
private const val MONTHS_PER_YEAR = 12L
private const val DAYS_PER_WEEK = 7L

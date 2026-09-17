package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.RecurringRule
import java.time.LocalDate

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

private const val MONTHS_PER_QUARTER = 3L

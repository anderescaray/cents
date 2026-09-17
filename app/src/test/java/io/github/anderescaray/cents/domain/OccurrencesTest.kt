package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.dueOccurrences
import io.github.anderescaray.cents.domain.usecase.nextOccurrenceAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * When a rule comes round.
 *
 * Nearly every case here is one that would be found months later on a real phone, as rent a day
 * early or a subscription that stopped appearing — the kind of wrong that is invisible while it
 * is happening. The month-end cases are the reason the arithmetic counts from the start date
 * rather than from the last occurrence.
 */
class OccurrencesTest {
    @Test
    fun `a monthly rule on the 31st survives February and comes back`() {
        val rule = rule(frequency = Frequency.MONTHLY, start = LocalDate.of(2026, 1, 31))

        val due = rule.dueOccurrences(today = LocalDate.of(2026, 4, 30))

        // February clamps, and March returns to the 31st. Stepping forward from the previous
        // occurrence would have pinned it to the 28th for the rest of the rule's life.
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
            ),
            due,
        )
    }

    @Test
    fun `a yearly rule on the 29th of February comes back on the leap year`() {
        val rule = rule(frequency = Frequency.YEARLY, start = LocalDate.of(2024, 2, 29))

        val due = rule.dueOccurrences(today = LocalDate.of(2028, 3, 1))

        assertEquals(
            listOf(
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2027, 2, 28),
                LocalDate.of(2028, 2, 29),
            ),
            due,
        )
    }

    @Test
    fun `a rule created today with a start date in the past has a backlog`() {
        val rule = rule(frequency = Frequency.MONTHLY, start = LocalDate.of(2026, 1, 15))

        val due = rule.dueOccurrences(today = LocalDate.of(2026, 4, 20))

        // Four charges, not one. Somebody entering their rent in April expects the year so far,
        // and generating only the newest would silently lose three months of history.
        assertEquals(4, due.size)
        assertEquals(LocalDate.of(2026, 1, 15), due.first())
    }

    @Test
    fun `nothing already generated is generated twice`() {
        val rule =
            rule(frequency = Frequency.MONTHLY, start = LocalDate.of(2026, 1, 15))
                .copy(lastGenerated = LocalDate.of(2026, 3, 15))

        val due = rule.dueOccurrences(today = LocalDate.of(2026, 4, 20))

        // The one property that must hold every time this runs: a catch-up is not a duplicate
        // machine, and WorkManager will run it more than once.
        assertEquals(listOf(LocalDate.of(2026, 4, 15)), due)
    }

    @Test
    fun `running it again the same day produces nothing`() {
        val rule =
            rule(frequency = Frequency.MONTHLY, start = LocalDate.of(2026, 1, 15))
                .copy(lastGenerated = LocalDate.of(2026, 4, 15))

        assertTrue(rule.dueOccurrences(today = LocalDate.of(2026, 4, 15)).isEmpty())
        assertTrue(rule.dueOccurrences(today = LocalDate.of(2026, 4, 20)).isEmpty())
    }

    @Test
    fun `a deactivated rule generates nothing, however long it has been`() {
        val rule =
            rule(frequency = Frequency.MONTHLY, start = LocalDate.of(2026, 1, 15))
                .copy(active = false)

        // "Cancel Netflix" means it stops, and that is enforced here rather than by whoever
        // remembers to check it (PRD §4.4).
        assertTrue(rule.dueOccurrences(today = LocalDate.of(2027, 1, 1)).isEmpty())
        assertNull(rule.nextOccurrenceAfter(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `an end date stops it, and the last charge still happens`() {
        val rule =
            rule(frequency = Frequency.MONTHLY, start = LocalDate.of(2026, 1, 10))
                .copy(endDate = LocalDate.of(2026, 3, 10))

        val due = rule.dueOccurrences(today = LocalDate.of(2026, 6, 1))

        // Inclusive: an end date is the day of the last charge, not the day before it.
        assertEquals(3, due.size)
        assertEquals(LocalDate.of(2026, 3, 10), due.last())
        assertNull(rule.nextOccurrenceAfter(LocalDate.of(2026, 3, 10)))
    }

    @Test
    fun `a rule that has not started yet is due nothing`() {
        val rule = rule(frequency = Frequency.MONTHLY, start = LocalDate.of(2026, 6, 1))

        assertTrue(rule.dueOccurrences(today = LocalDate.of(2026, 5, 31)).isEmpty())
        assertEquals(LocalDate.of(2026, 6, 1), rule.nextOccurrenceAfter(LocalDate.of(2026, 5, 31)))
    }

    @Test
    fun `weekly and fortnightly keep the day of the week`() {
        val monday = LocalDate.of(2026, 9, 7)
        val weekly = rule(frequency = Frequency.WEEKLY, start = monday)
        val fortnightly = rule(frequency = Frequency.FORTNIGHTLY, start = monday)

        assertEquals(LocalDate.of(2026, 9, 14), weekly.nextOccurrenceAfter(monday))
        assertEquals(LocalDate.of(2026, 9, 21), fortnightly.nextOccurrenceAfter(monday))
    }

    @Test
    fun `every N days counts days rather than months`() {
        val rule =
            rule(frequency = Frequency.EVERY_N_DAYS, start = LocalDate.of(2026, 1, 1))
                .copy(intervalDays = 10)

        assertEquals(LocalDate.of(2026, 1, 11), rule.nextOccurrenceAfter(LocalDate.of(2026, 1, 1)))
        assertEquals(4, rule.dueOccurrences(today = LocalDate.of(2026, 1, 31)).size)
    }

    @Test
    fun `an interval nobody filled in comes round never, rather than for ever`() {
        val rule = rule(frequency = Frequency.EVERY_N_DAYS, start = LocalDate.of(2026, 1, 1))

        // The difference between a half-filled form and an infinite loop: with no N, each step
        // would land on the same date and the sequence would never end.
        assertTrue(rule.dueOccurrences(today = LocalDate.of(2027, 1, 1)).isEmpty())
        assertNull(rule.nextOccurrenceAfter(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `a quarterly rule lands three months on, not ninety days`() {
        val rule = rule(frequency = Frequency.QUARTERLY, start = LocalDate.of(2026, 1, 31))

        assertEquals(LocalDate.of(2026, 4, 30), rule.nextOccurrenceAfter(LocalDate.of(2026, 1, 31)))
    }

    private fun rule(
        frequency: Frequency,
        start: LocalDate,
    ) = RecurringRule(
        id = 1,
        name = "Netflix",
        accountId = 1,
        categoryId = null,
        kind = TransactionKind.EXPENSE,
        amount = Money(1_299, "EUR"),
        frequency = frequency,
        intervalDays = null,
        startDate = start,
        endDate = null,
        mode = RuleMode.AUTOMATIC,
        active = true,
        lastGenerated = null,
    )
}

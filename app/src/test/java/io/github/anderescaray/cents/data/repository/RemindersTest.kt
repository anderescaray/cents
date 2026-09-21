package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.occursOn
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Being told before the money goes.
 *
 * The design being tested is that **nothing is scheduled in advance**: once a day the app asks
 * *what lands today*, and the answer is computed from the rules. So the tests are all of the form
 * "on this day, what would fire" — which is also the only question the worker ever asks.
 */
@RunWith(RobolectricTestRunner::class)
class RemindersTest {
    private lateinit var database: CentsDatabase
    private var accountId: Long = 0

    @Before
    fun setUp() =
        runTest {
            database = createTestDatabase()
            accountId = database.accountDao().insert(account(name = "Checking"))
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a reminder three days before lands three days before`() =
        runTest {
            val id = rules(on = "2026-09-01").createRule(monthly(start = LocalDate.of(2026, 9, 10)))
            rules(on = "2026-09-01").setReminders(id, listOf(-3))

            // The 7th is three days before the 10th, and nothing else is.
            assertTrue(fires(on = "2026-09-07"))
            assertFalse(fires(on = "2026-09-06"))
            assertFalse(fires(on = "2026-09-08"))
            assertFalse(fires(on = "2026-09-10"))
        }

    @Test
    fun `it carries the date it is about, not the date it fires`() =
        runTest {
            val id = rules(on = "2026-09-01").createRule(monthly(start = LocalDate.of(2026, 9, 10)))
            rules(on = "2026-09-01").setReminders(id, listOf(-3))

            val due = rules(on = "2026-09-07").dueReminders(LocalDate.of(2026, 9, 7)).single()

            // "The rent is on the 10th" is only sayable because the occurrence travels with it.
            assertEquals(LocalDate.of(2026, 9, 10), due.occurrence)
            assertEquals(-3, due.offsetDays)
        }

    @Test
    fun `several reminders on one subscription all fire, each on its own day`() =
        runTest {
            val id = rules(on = "2026-09-01").createRule(monthly(start = LocalDate.of(2026, 9, 10)))
            rules(on = "2026-09-01").setReminders(id, listOf(-3, 0))

            // "Three days before and again on the day" is a real way to be reminded.
            assertTrue(fires(on = "2026-09-07"))
            assertTrue(fires(on = "2026-09-10"))
            assertFalse(fires(on = "2026-09-09"))
        }

    @Test
    fun `the same reminder twice is stored once`() =
        runTest {
            val id = rules(on = "2026-09-01").createRule(monthly(start = LocalDate.of(2026, 9, 10)))
            rules(on = "2026-09-01").setReminders(id, listOf(-3, -3, -3))

            // The same notification three times is how somebody turns notifications off.
            assertEquals(listOf(-3), rules(on = "2026-09-01").remindersFor(id).map { it.offsetDays })
        }

    @Test
    fun `setting reminders again replaces what was there`() =
        runTest {
            val id = rules(on = "2026-09-01").createRule(monthly(start = LocalDate.of(2026, 9, 10)))
            rules(on = "2026-09-01").setReminders(id, listOf(-7, -1))

            rules(on = "2026-09-01").setReminders(id, listOf(0))

            assertEquals(listOf(0), rules(on = "2026-09-01").remindersFor(id).map { it.offsetDays })
        }

    @Test
    fun `a month-end rule reminds about the day it really lands on`() =
        runTest {
            // The 31st clamps to the 28th in February, so "two days before" is the 26th — not
            // the 29th, which does not exist. Both halves come from the same definition of
            // where a rule lands, which is why they cannot disagree.
            val id = rules(on = "2026-01-01").createRule(monthly(start = LocalDate.of(2026, 1, 31)))
            rules(on = "2026-01-01").setReminders(id, listOf(-2))

            assertTrue(fires(on = "2026-02-26"))
            assertFalse(fires(on = "2026-02-27"))

            val due = rules(on = "2026-02-26").dueReminders(LocalDate.of(2026, 2, 26)).single()
            assertEquals(LocalDate.of(2026, 2, 28), due.occurrence)
        }

    @Test
    fun `a stopped subscription says nothing`() =
        runTest {
            val id = rules(on = "2026-09-01").createRule(monthly(start = LocalDate.of(2026, 9, 10)))
            rules(on = "2026-09-01").setReminders(id, listOf(-3))

            rules(on = "2026-09-01").setActive(id, active = false)

            // Cancelling Netflix cannot keep announcing it.
            assertFalse(fires(on = "2026-09-07"))
        }

    @Test
    fun `nothing fires past the end date`() =
        runTest {
            val id =
                rules(on = "2026-09-01").createRule(
                    monthly(start = LocalDate.of(2026, 9, 10)).copy(endDate = LocalDate.of(2026, 9, 30)),
                )
            rules(on = "2026-09-01").setReminders(id, listOf(-3))

            assertTrue(fires(on = "2026-09-07"))
            assertFalse(fires(on = "2026-10-07"))
        }

    @Test
    fun `a variable bill already answered stops being chased`() =
        runTest {
            val id =
                rules(on = "2026-09-01").createRule(
                    monthly(start = LocalDate.of(2026, 9, 10)).copy(
                        name = "Electricity",
                        mode = RuleMode.CONFIRM,
                    ),
                )
            rules(on = "2026-09-01").setReminders(id, listOf(2))

            // The reminder two days after the 10th would land on the 12th — unless the bill has
            // been entered by then, which is the whole reason it was set.
            assertTrue(fires(on = "2026-09-12"))
            rules(on = "2026-09-11").recordPending(id, LocalDate.of(2026, 9, 10), Money(7_412, "EUR"))
            assertFalse(fires(on = "2026-09-12"))
        }

    @Test
    fun `a fixed subscription is announced whether or not it has been recorded`() =
        runTest {
            val id = rules(on = "2026-09-01").createRule(monthly(start = LocalDate.of(2026, 9, 10)))
            rules(on = "2026-09-01").setReminders(id, listOf(-3))
            rules(on = "2026-09-20").generateDue()

            // The point of a heads-up is being told *before* the money goes, so the charge
            // already existing must not silence next month's.
            assertTrue(fires(on = "2026-10-07"))
        }

    @Test
    fun `a rule with no reminders never appears`() =
        runTest {
            rules(on = "2026-09-01").createRule(monthly(start = LocalDate.of(2026, 9, 10)))

            assertFalse(fires(on = "2026-09-07"))
            assertFalse(fires(on = "2026-09-10"))
        }

    @Test
    fun `occursOn agrees with the list of occurrences it would generate`() {
        val rule = monthly(start = LocalDate.of(2026, 1, 31))

        // One definition of where a rule lands, used by the generator and by the reminders.
        assertTrue(rule.occursOn(LocalDate.of(2026, 1, 31)))
        assertTrue(rule.occursOn(LocalDate.of(2026, 2, 28)))
        assertTrue(rule.occursOn(LocalDate.of(2026, 3, 31)))
        assertFalse(rule.occursOn(LocalDate.of(2026, 2, 27)))
        assertFalse(rule.occursOn(LocalDate.of(2026, 3, 30)))
        assertFalse("before it began", rule.occursOn(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun `occursOn handles the frequencies that count in days`() {
        val weekly = monthly(start = LocalDate.of(2026, 9, 1)).copy(frequency = Frequency.WEEKLY)
        assertTrue(weekly.occursOn(LocalDate.of(2026, 9, 8)))
        assertFalse(weekly.occursOn(LocalDate.of(2026, 9, 9)))

        val everyTen =
            monthly(start = LocalDate.of(2026, 9, 1)).copy(
                frequency = Frequency.EVERY_N_DAYS,
                intervalDays = 10,
            )
        assertTrue(everyTen.occursOn(LocalDate.of(2026, 9, 21)))
        assertFalse(everyTen.occursOn(LocalDate.of(2026, 9, 22)))

        val yearly = monthly(start = LocalDate.of(2024, 2, 29)).copy(frequency = Frequency.YEARLY)
        assertTrue("a leap day falls back to the 28th", yearly.occursOn(LocalDate.of(2025, 2, 28)))
        assertTrue("and returns when a leap year comes", yearly.occursOn(LocalDate.of(2028, 2, 29)))
    }

    private suspend fun fires(on: String): Boolean = rules(on = on).dueReminders(LocalDate.parse(on)).isNotEmpty()

    private fun rules(on: String) =
        RoomRecurringRuleRepository(
            database.recurringRuleDao(),
            database.ruleReminderDao(),
            database,
            Clock.fixed(Instant.parse("${on}T09:00:00Z"), ZoneOffset.UTC),
        )

    private fun monthly(start: LocalDate) =
        RecurringRule(
            id = 0,
            name = "Rent",
            accountId = accountId,
            categoryId = null,
            kind = TransactionKind.EXPENSE,
            amount = Money(75_000, "EUR"),
            frequency = Frequency.MONTHLY,
            intervalDays = null,
            startDate = start,
            endDate = null,
            mode = RuleMode.AUTOMATIC,
            active = true,
            lastGenerated = null,
        )
}

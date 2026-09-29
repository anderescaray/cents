package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.postponeChoices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * Moving one occurrence to a later day (`DECISIONS.md` D28).
 *
 * What must hold: the movement carries the new day, it is written once, and **the rule's own
 * schedule does not move** — the next month still falls where it always did.
 */
@RunWith(RobolectricTestRunner::class)
class PostponeTest {
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
    fun `a postponed rent is written on its new day, and next month keeps its own`() =
        runTest {
            val id = rules(on = "2026-09-25").createRule(rent())
            rules(on = "2026-09-25").generateDue()
            assertTrue(rules(on = "2026-09-25").postpone(id, OCT_1, OCT_4))

            // The 1st passes and nothing is written.
            assertEquals(0, rules(on = "2026-10-01").generateDue())
            assertEquals(listOf(SEP_1), movementDates())

            // The 4th writes it, dated the 4th, once.
            assertEquals(1, rules(on = "2026-10-04").generateDue())
            assertEquals(0, rules(on = "2026-10-04").generateDue())
            assertEquals(listOf(SEP_1, OCT_4), movementDates())

            // November is on the 1st, as it always was.
            rules(on = "2026-11-01").generateDue()
            assertEquals(listOf(SEP_1, OCT_4, LocalDate.of(2026, 11, 1)), movementDates())
            assertTrue("a written postponement is forgotten", database.rulePostponementDao().all().isEmpty())
        }

    @Test
    fun `an occurrence already written, or a day that reaches the next one, is refused`() =
        runTest {
            val id = rules(on = "2026-09-25").createRule(rent())
            rules(on = "2026-09-25").generateDue()

            // September is a movement now: its date is edited there, not here.
            assertFalse(rules(on = "2026-09-25").postpone(id, SEP_1, LocalDate.of(2026, 9, 28)))
            // October may not overtake November.
            assertFalse(rules(on = "2026-09-25").postpone(id, OCT_1, LocalDate.of(2026, 11, 1)))
            // Nor go backwards.
            assertFalse(rules(on = "2026-09-25").postpone(id, OCT_1, OCT_1))
            assertTrue(database.rulePostponementDao().all().isEmpty())
        }

    @Test
    fun `put back on its own day, it is written on its own day`() =
        runTest {
            val id = rules(on = "2026-09-25").createRule(rent())
            rules(on = "2026-09-25").generateDue()
            rules(on = "2026-09-25").postpone(id, OCT_1, OCT_4)

            rules(on = "2026-09-26").cancelPostponement(id, OCT_1)
            rules(on = "2026-10-01").generateDue()

            assertEquals(listOf(SEP_1, OCT_1), movementDates())
        }

    @Test
    fun `a postponed variable salary waits until its new day and is recorded on it`() =
        runTest {
            val id =
                rules(on = "2026-09-25").createRule(
                    rent().copy(name = "Salary", kind = TransactionKind.INCOME, mode = RuleMode.CONFIRM),
                )
            // September is waiting, and the salary is late: three days from today.
            val september = rules(on = "2026-09-25").observePending().first().single()
            assertEquals(SEP_1, september.dueDate)
            assertTrue(rules(on = "2026-09-25").postpone(id, SEP_1, SEP_28))

            assertTrue("not waiting before its new day", rules(on = "2026-09-27").observePending().first().isEmpty())

            val due = rules(on = "2026-09-28").observePending().first().single()
            assertEquals(SEP_28, due.recordOn)
            assertTrue(rules(on = "2026-09-28").recordPending(id, SEP_1, Money(210_000, "EUR")))

            val salary =
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .single()
            assertEquals(SEP_28, salary.date)
            assertEquals(210_000L, salary.amountMinor)
            assertEquals(TransactionKind.INCOME, salary.kind)
            assertTrue(database.rulePostponementDao().all().isEmpty())
        }

    @Test
    fun `a postponed occurrence is announced on its new day, not its old one`() =
        runTest {
            // No reminders set at all: postponing is itself asking to be told.
            val id = rules(on = "2026-09-25").createRule(rent())
            rules(on = "2026-09-25").generateDue()
            rules(on = "2026-09-25").postpone(id, OCT_1, OCT_4)

            assertTrue(rules(on = "2026-10-01").dueReminders(OCT_1).isEmpty())
            val onTheDay = rules(on = "2026-10-04").dueReminders(OCT_4).single()
            assertEquals(OCT_1, onTheDay.occurrence)
            assertEquals(OCT_4, onTheDay.postponedTo)
            assertEquals(0, onTheDay.offsetDays)
        }

    @Test
    fun `a rule's own reminder on the day moves with it rather than firing twice`() =
        runTest {
            val id = rules(on = "2026-09-25").createRule(rent())
            rules(on = "2026-09-25").setReminders(id, listOf(0))
            rules(on = "2026-09-25").generateDue()
            rules(on = "2026-09-25").postpone(id, OCT_1, OCT_4)

            assertTrue(rules(on = "2026-10-01").dueReminders(OCT_1).isEmpty())
            assertEquals(1, rules(on = "2026-10-04").dueReminders(OCT_4).size)
        }

    @Test
    fun `an edit that leaves a move past the new next occurrence drops it`() =
        runTest {
            val id = rules(on = "2026-09-25").createRule(rent())
            rules(on = "2026-09-25").generateDue()
            assertTrue(rules(on = "2026-09-25").postpone(id, OCT_1, OCT_14))

            // Weekly from Thursday 3 September: 1 October is still one of its days, but the next
            // one is the 8th, and a move to the 14th would overtake it.
            val saved = requireNotNull(rules(on = "2026-09-25").findById(id))
            rules(on = "2026-09-25").updateRule(saved.copy(frequency = Frequency.WEEKLY, startDate = SEP_3))

            assertTrue(database.rulePostponementDao().all().isEmpty())
        }

    @Test
    fun `an edit that moves the rule off the moved day drops the move`() =
        runTest {
            val id = rules(on = "2026-09-25").createRule(rent())
            rules(on = "2026-09-25").generateDue()
            assertTrue(rules(on = "2026-09-25").postpone(id, OCT_1, OCT_4))

            // Monthly on the 2nd from now on: 1 October is no longer one of its days.
            val saved = requireNotNull(rules(on = "2026-09-25").findById(id))
            rules(on = "2026-09-25").updateRule(saved.copy(startDate = LocalDate.of(2026, 9, 2)))

            assertTrue(database.rulePostponementDao().all().isEmpty())
        }

    @Test
    fun `an edit that still fits the move keeps it`() =
        runTest {
            val id = rules(on = "2026-09-25").createRule(rent())
            rules(on = "2026-09-25").generateDue()
            assertTrue(rules(on = "2026-09-25").postpone(id, OCT_1, OCT_4))

            val saved = requireNotNull(rules(on = "2026-09-25").findById(id))
            rules(on = "2026-09-25").updateRule(saved.copy(name = "Flat", amount = Money(80_000, "EUR")))

            assertEquals(
                listOf(OCT_1 to OCT_4),
                database.rulePostponementDao().all().map { it.occurrence to it.postponedTo },
            )
        }

    @Test
    fun `the choices stop short of the next occurrence`() {
        val weekly = rent().copy(frequency = Frequency.WEEKLY, startDate = OCT_1)

        val choices = weekly.postponeChoices(OCT_1, today = LocalDate.of(2026, 9, 25))

        // One, two, three and five days fit before the 8th; a week does not.
        assertEquals(listOf(2, 3, 4, 6), choices.map { it.dayOfMonth })
        assertNull(choices.firstOrNull { it >= LocalDate.of(2026, 10, 8) })
    }

    private suspend fun movementDates(): List<LocalDate> =
        database
            .transactionDao()
            .observeAll()
            .first()
            .map { it.date }
            .sorted()

    private fun rules(on: String) =
        RoomRecurringRuleRepository(
            database.recurringRuleDao(),
            database.ruleReminderDao(),
            database,
            Clock.fixed(Instant.parse("${on}T09:00:00Z"), ZoneOffset.UTC),
        )

    private fun rent() =
        RecurringRule(
            id = 0,
            name = "Rent",
            accountId = accountId,
            categoryId = null,
            kind = TransactionKind.EXPENSE,
            amount = Money(75_000, "EUR"),
            frequency = Frequency.MONTHLY,
            intervalDays = null,
            startDate = SEP_1,
            endDate = null,
            mode = RuleMode.AUTOMATIC,
            active = true,
            lastGenerated = null,
        )

    private companion object {
        val SEP_1: LocalDate = LocalDate.of(2026, 9, 1)
        val SEP_3: LocalDate = LocalDate.of(2026, 9, 3)
        val SEP_28: LocalDate = LocalDate.of(2026, 9, 28)
        val OCT_1: LocalDate = LocalDate.of(2026, 10, 1)
        val OCT_4: LocalDate = LocalDate.of(2026, 10, 4)
        val OCT_14: LocalDate = LocalDate.of(2026, 10, 14)
    }
}

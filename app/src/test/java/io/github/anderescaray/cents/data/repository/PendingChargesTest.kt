package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
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
 * The bills the app refuses to guess, and what happens when they are answered.
 *
 * `generateDue` skips a variable rule on purpose; this is where what it skipped goes. The thing
 * to get wrong here is **losing a period**: a rule remembers how far it has got with a single
 * date, so answering out of order would swallow everything before it without saying so.
 */
@RunWith(RobolectricTestRunner::class)
class PendingChargesTest {
    private lateinit var database: CentsDatabase

    private var accountId: Long = 0
    private var categoryId: Long = 0

    /** Mid-April, so a rule starting in January has four periods behind it. */
    private val april = Instant.parse("2026-04-20T09:00:00Z")

    @Before
    fun setUp() =
        runTest {
            database = createTestDatabase()
            accountId = database.accountDao().insert(account(name = "Checking"))
            categoryId = database.categoryDao().insert(category(name = "Bills"))
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a variable rule that has fallen due is waiting rather than recorded`() =
        runTest {
            val rules = repository(april)
            rules.createRule(variable(start = LocalDate.of(2026, 1, 15)))

            assertEquals(0, rules.generateDue())

            val pending = rules.observePending().first().single()
            assertEquals("Electricity", pending.rule.name)
            assertEquals(LocalDate.of(2026, 1, 15), pending.dueDate)
            // Four have fallen due; only the oldest is offered, and the row says so.
            assertEquals(3, pending.laterCount)
            assertTrue(
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `a rule with a fixed amount never waits, because it has nothing to ask`() =
        runTest {
            val rules = repository(april)
            rules.createRule(
                variable(start = LocalDate.of(2026, 1, 15)).copy(name = "Netflix", mode = RuleMode.AUTOMATIC),
            )
            rules.generateDue()

            assertTrue(rules.observePending().first().isEmpty())
        }

    @Test
    fun `answering one writes it on the day it fell due, not today`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(variable(start = LocalDate.of(2026, 1, 15)))

            assertTrue(rules.recordPending(id, LocalDate.of(2026, 1, 15), Money(7_412, "EUR")))

            // The period it was for, not the day somebody got round to it. Dating it by when it
            // was answered would move money between months in every chart in the app.
            val written =
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .single()
            assertEquals(LocalDate.of(2026, 1, 15), written.date)
            assertEquals(7_412L, written.amountMinor)
            assertEquals(id, written.ruleId)
            assertEquals(categoryId, written.categoryId)
        }

    @Test
    fun `answering the oldest reveals the next one`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(variable(start = LocalDate.of(2026, 1, 15)))

            rules.recordPending(id, LocalDate.of(2026, 1, 15), Money(7_412, "EUR"))

            val next = rules.observePending().first().single()
            assertEquals(LocalDate.of(2026, 2, 15), next.dueDate)
            assertEquals(2, next.laterCount)
        }

    @Test
    fun `answering one that is not the oldest is refused, and writes nothing`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(variable(start = LocalDate.of(2026, 1, 15)))

            // The failure this guard exists for: the rule remembers one date, so recording
            // March would move the watermark past January and February and neither would ever
            // be asked about again. A screen left open while something else answered looks
            // exactly like this.
            assertFalse(rules.recordPending(id, LocalDate.of(2026, 3, 15), Money(6_000, "EUR")))

            assertTrue(
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
            assertEquals(
                LocalDate.of(2026, 1, 15),
                rules
                    .observePending()
                    .first()
                    .single()
                    .dueDate,
            )
        }

    @Test
    fun `an amount in the wrong currency is refused rather than written as a number`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(variable(start = LocalDate.of(2026, 1, 15)))

            // Rule 2 at the one place a figure arrives from outside: 60 of something else is
            // not 60 euros, and this account is in euros.
            assertFalse(rules.recordPending(id, LocalDate.of(2026, 1, 15), Money(6_000, "USD")))
            assertTrue(
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `skipping moves past a month there was no bill for, and records nothing`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(variable(start = LocalDate.of(2026, 1, 15)))

            assertTrue(rules.skipPending(id, LocalDate.of(2026, 1, 15)))

            // Not a zero: a zero would sit in the charts saying the month cost nothing, and
            // this is the absence of a figure rather than a figure.
            assertTrue(
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
            assertEquals(
                LocalDate.of(2026, 2, 15),
                rules
                    .observePending()
                    .first()
                    .single()
                    .dueDate,
            )
        }

    @Test
    fun `answering every one of them empties the list`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(variable(start = LocalDate.of(2026, 1, 15)))

            listOf(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 4, 15),
            ).forEach { date -> assertTrue(rules.recordPending(id, date, Money(7_000, "EUR"))) }

            assertTrue(rules.observePending().first().isEmpty())
            assertEquals(
                4,
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size,
            )
            // And a run of the generator still writes nothing: it never touched these.
            assertEquals(0, rules.generateDue())
        }

    @Test
    fun `a stopped rule stops asking`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(variable(start = LocalDate.of(2026, 1, 15)))

            rules.setActive(id, active = false)

            // "Cancel the electricity" cannot keep nagging about months that will never come.
            assertTrue(rules.observePending().first().isEmpty())
        }

    @Test
    fun `a bill answered late still counts in the month it was for`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(variable(start = LocalDate.of(2026, 1, 15)))
            rules.recordPending(id, LocalDate.of(2026, 1, 15), Money(7_412, "EUR"))

            val january =
                database
                    .transactionDao()
                    .observeTotalsBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
                    .first()

            assertEquals(7_412L, january.expenseMinor)
        }

    private fun repository(now: Instant) =
        RoomRecurringRuleRepository(
            database.recurringRuleDao(),
            database.ruleReminderDao(),
            database,
            Clock.fixed(now, ZoneOffset.UTC),
        )

    private fun variable(start: LocalDate) =
        RecurringRule(
            id = 0,
            name = "Electricity",
            accountId = accountId,
            categoryId = categoryId,
            kind = TransactionKind.EXPENSE,
            // What it is expected to be. It stands in for the real figure in the monthly total
            // and as the placeholder on the screen that asks.
            amount = Money(6_000, "EUR"),
            frequency = Frequency.MONTHLY,
            intervalDays = null,
            startDate = start,
            endDate = null,
            mode = RuleMode.CONFIRM,
            active = true,
            lastGenerated = null,
        )
}

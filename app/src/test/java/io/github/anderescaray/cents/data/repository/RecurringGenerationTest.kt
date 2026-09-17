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
 * A rule makes the movements, and never makes them twice.
 *
 * Generation runs whenever the app opens, so it is going to run again — the failure to design
 * against is a charge written twice, not one written late. Everything below is either about that
 * or about rule 8: cancelling a subscription must not reach back into the past.
 */
@RunWith(RobolectricTestRunner::class)
class RecurringGenerationTest {
    private lateinit var database: CentsDatabase

    private var accountId: Long = 0
    private var categoryId: Long = 0

    /** Mid-April, so a rule starting in January has a backlog. */
    private val april = Instant.parse("2026-04-20T09:00:00Z")

    @Before
    fun setUp() =
        runTest {
            database = createTestDatabase()
            accountId = database.accountDao().insert(account(name = "Checking"))
            categoryId = database.categoryDao().insert(category(name = "Subscriptions"))
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a rule started in January writes the months it missed`() =
        runTest {
            val rules = repository(april)
            rules.createRule(monthly(start = LocalDate.of(2026, 1, 15)))

            assertEquals(4, rules.generateDue())

            val written = database.transactionDao().observeAll().first()
            assertEquals(4, written.size)
            assertEquals(
                listOf(15, 15, 15, 15),
                written.map { it.date.dayOfMonth },
            )
            assertEquals(1_299L, written.first().amountMinor)
            assertEquals(categoryId, written.first().categoryId)
        }

    @Test
    fun `running it again writes nothing`() =
        runTest {
            val rules = repository(april)
            rules.createRule(monthly(start = LocalDate.of(2026, 1, 15)))
            rules.generateDue()

            // The property everything else rests on. It runs on every launch, and again from
            // whatever schedules it later.
            assertEquals(0, rules.generateDue())
            assertEquals(
                4,
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun `a movement it made is an ordinary movement`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(monthly(start = LocalDate.of(2026, 4, 1)))
            rules.generateDue()

            val written =
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .single()

            // It counts in every report, because rent made by a rule is rent. Only the link
            // back to the template is new.
            assertEquals(id, written.ruleId)
            assertEquals("Netflix", written.note)
            val from = LocalDate.of(2026, 4, 1)
            val to = LocalDate.of(2026, 4, 30)
            assertEquals(
                1_299L,
                database
                    .transactionDao()
                    .observeTotalsBetween(from, to)
                    .first()
                    .expenseMinor,
            )
        }

    @Test
    fun `cancelling a subscription stops it without touching the past`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(monthly(start = LocalDate.of(2026, 1, 15)))
            rules.generateDue()

            rules.setActive(id, active = false)

            // The sentence PRD §4.4 insists on: cancel Netflix in April and January to March
            // are still there, because every chart of the past would otherwise change.
            assertEquals(0, repository(Instant.parse("2027-01-01T09:00:00Z")).generateDue())
            assertEquals(
                4,
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun `a rule that has produced nothing can be deleted outright`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(monthly(start = LocalDate.of(2026, 6, 1)))

            assertEquals(0, rules.countGenerated(id))
            rules.deleteRule(id)

            assertTrue(rules.observeRules().first().isEmpty())
        }

    @Test
    fun `deleting a rule that did produce something leaves the movements standing`() =
        runTest {
            val rules = repository(april)
            val id = rules.createRule(monthly(start = LocalDate.of(2026, 1, 15)))
            rules.generateDue()
            assertEquals(4, rules.countGenerated(id))

            // Deactivating is the honest operation here, and the UI offers that — but the
            // schema has to survive the other one too. `SET NULL` is rule 8 in the schema: the
            // history stands, orphaned from a template rather than deleted with it.
            rules.deleteRule(id)

            val written = database.transactionDao().observeAll().first()
            assertEquals(4, written.size)
            assertTrue(written.all { it.ruleId == null })
        }

    @Test
    fun `a rule that asks to be confirmed writes nothing on its own`() =
        runTest {
            val rules = repository(april)
            rules.createRule(
                monthly(start = LocalDate.of(2026, 1, 15)).copy(mode = RuleMode.CONFIRM, name = "Electricity"),
            )

            // An amount that varies is not one the app may assert on the owner's behalf. The
            // proposals that replace this are their own branch; until then nothing is invented.
            assertEquals(0, rules.generateDue())
            assertTrue(
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
            // And nothing is lost: the watermark is untouched, so the backlog is still waiting.
            assertNull(
                rules
                    .observeRules()
                    .first()
                    .single()
                    .lastGenerated,
            )
        }

    @Test
    fun `income repeats the same way a charge does`() =
        runTest {
            val rules = repository(april)
            rules.createRule(
                monthly(start = LocalDate.of(2026, 3, 1)).copy(
                    name = "Salary",
                    kind = TransactionKind.INCOME,
                    amount = Money(250_000, "EUR"),
                ),
            )

            rules.generateDue()

            // "Works identically for income: a salary is just a recurring rule" (PRD §4.4).
            val written = database.transactionDao().observeAll().first()
            assertEquals(2, written.size)
            assertTrue(written.all { it.kind == TransactionKind.INCOME })
        }

    @Test
    fun `a rule stops at its end date`() =
        runTest {
            val rules = repository(april)
            rules.createRule(
                monthly(start = LocalDate.of(2026, 1, 15)).copy(endDate = LocalDate.of(2026, 2, 15)),
            )

            assertEquals(2, rules.generateDue())
            assertEquals(0, repository(Instant.parse("2027-01-01T09:00:00Z")).generateDue())
        }

    private fun repository(now: Instant) =
        RoomRecurringRuleRepository(
            database.recurringRuleDao(),
            database,
            Clock.fixed(now, ZoneOffset.UTC),
        )

    private fun monthly(start: LocalDate) =
        RecurringRule(
            id = 0,
            name = "Netflix",
            accountId = accountId,
            categoryId = categoryId,
            kind = TransactionKind.EXPENSE,
            amount = Money(1_299, "EUR"),
            frequency = Frequency.MONTHLY,
            intervalDays = null,
            startDate = start,
            endDate = null,
            mode = RuleMode.AUTOMATIC,
            active = true,
            lastGenerated = null,
        )
}

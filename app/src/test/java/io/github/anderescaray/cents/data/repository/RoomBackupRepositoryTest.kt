package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.entity.RuleReminderEntity
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.quote
import io.github.anderescaray.cents.data.local.recurringRule
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The snapshot has to contain everything, or the promise is false.
 *
 * "So I am never locked into the app" (PRD §4.7) is only true if a table that exists is a table
 * that is exported. A row type added later and not added here would leave a backup that looks
 * complete and is not — which is why this asks for each table by name rather than counting.
 */
@RunWith(RobolectricTestRunner::class)
class RoomBackupRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var backups: RoomBackupRepository

    private val clock = Clock.fixed(Instant.parse("2026-09-16T18:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = createTestDatabase()
        backups = RoomBackupRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `every table reaches the file`() =
        runTest {
            val accountId = database.accountDao().insert(account(name = "Checking"))
            val categoryId = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insert(
                transaction(accountId, categoryId = categoryId, amountMinor = 2_550),
            )
            val holdingId = database.holdingDao().insert(holding(accountId, symbol = "NVD.DE"))
            database.tradeDao().insert(trade(holdingId))
            database.priceQuoteDao().upsert(quote(symbol = "NVD.DE"))
            val ruleId = database.recurringRuleDao().insert(recurringRule(accountId))
            database.ruleReminderDao().insert(RuleReminderEntity(ruleId = ruleId, offsetDays = -3))

            val backup = backups.snapshot()

            assertEquals(1, backup.accounts.size)
            assertEquals(1, backup.categories.size)
            assertEquals(1, backup.transactions.size)
            assertEquals(1, backup.holdings.size)
            assertEquals(1, backup.trades.size)
            assertEquals(1, backup.quotes.size)
            assertEquals(1, backup.rules.size)
            assertEquals(1, backup.reminders.size)
            assertEquals(8, backup.rowCount)
        }

    @Test
    fun `it carries the version of the schema that wrote it`() =
        runTest {
            val backup = backups.snapshot()

            // Whatever reads this next has to be able to refuse rather than guess.
            assertEquals(CentsDatabase.VERSION, backup.schemaVersion)
            assertEquals(Instant.parse("2026-09-16T18:00:00Z"), backup.exportedAt)
        }

    @Test
    fun `rows keep their ids and their exact amounts`() =
        runTest {
            val accountId = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
            val id =
                database.transactionDao().insert(
                    transaction(
                        accountId,
                        kind = TransactionKind.INCOME,
                        amountMinor = 123_456,
                        date = LocalDate.of(2026, 7, 4),
                        note = "Salary",
                    ),
                )

            val movement = backups.snapshot().transactions.single()

            // Ids travel because a backup is a graph: a movement that lost which account it
            // was in would be a number with nowhere to go back to.
            assertEquals(id, movement.id)
            assertEquals(accountId, movement.accountId)
            assertEquals(123_456L, movement.amountMinor)
            assertEquals(LocalDate.of(2026, 7, 4).toEpochDay(), movement.date)
            assertEquals("INCOME", movement.kind)
            assertEquals("Salary", movement.note)
            assertEquals(
                100_000L,
                backups
                    .snapshot()
                    .accounts
                    .single()
                    .openingBalanceMinor,
            )
        }

    @Test
    fun `what a transfer and a trade tie together is exported too`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            val dao = database.transactionDao()
            val outId = dao.insert(transaction(checking, amountMinor = 50_000))
            dao.setTransferId(id = outId, transferId = outId, updatedAt = clock.instant())
            dao.insert(
                transaction(savings, kind = TransactionKind.INCOME, amountMinor = 50_000, transferId = outId),
            )

            val legs = backups.snapshot().transactions.filter { it.transferId != null }

            // Without the pair id the two legs are an unexplained expense and an unexplained
            // income, and a restored file would inflate both halves of every report.
            assertEquals(2, legs.size)
            assertEquals(setOf(outId), legs.mapNotNull { it.transferId }.toSet())
        }

    @Test
    fun `a fresh install exports a file that says there is nothing yet`() =
        runTest {
            val backup = backups.snapshot()

            assertEquals(0, backup.rowCount)
            assertEquals(emptyList<Any>(), backup.accounts)
        }

    @Test
    fun `an uncategorized movement stays uncategorized`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insert(transaction(accountId, categoryId = null))

            assertNull(
                backups
                    .snapshot()
                    .transactions
                    .single()
                    .categoryId,
            )
        }
}

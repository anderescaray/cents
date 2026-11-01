package io.github.anderescaray.cents.data.local

import android.database.sqlite.SQLiteConstraintException
import io.github.anderescaray.cents.data.local.entity.BalanceAdjustmentEntity
import io.github.anderescaray.cents.data.local.entity.CaptureMatchEntity
import io.github.anderescaray.cents.data.local.entity.RulePostponementEntity
import io.github.anderescaray.cents.data.local.entity.RuleReminderEntity
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/**
 * Every migration this database has, tested from the schema that actually shipped.
 *
 * **What it proves.** A database is built from an *exported* schema file rather than from
 * hand-written SQL — so it is the version that really ran on the phone — rows are put in it,
 * and then Room is opened on it with the migrations. Two things then have to hold, and the
 * second is the one that catches a typo in a migration:
 *
 * 1. **Nothing already recorded is lost.** The account and the movement written before the
 *    migration are still there afterwards, with the same values.
 * 2. **Room accepts the result.** After running a migration, Room compares the database
 *    against the schema it expects and refuses to open one that differs by so much as an
 *    index. So a migration that creates a column of the wrong type, or forgets an index,
 *    fails here rather than on the owner's phone — where it would mean an app that cannot
 *    start.
 *
 * Both hops are covered, and from both ends: an install that predates investments migrates
 * the whole way in one go, and one that stopped at v2 takes the single step to v3.
 */
@RunWith(RobolectricTestRunner::class)
class CentsDatabaseMigrationTest {
    private lateinit var old: MigrationTestDatabase

    @Before
    fun setUp() {
        old = MigrationTestDatabase()
    }

    @Test
    fun `a version 1 database keeps its money and gains the investments tables`() =
        runTest {
            createVersion(1)

            val database = openWithMigrations()
            try {
                val accounts = database.accountDao().observeAll().first()
                assertEquals("the account written at v1 should survive", 1, accounts.size)
                assertEquals("Checking", accounts.single().name)
                assertEquals(10_000L, accounts.single().openingBalanceMinor)

                val transactions = database.transactionDao().observeAll().first()
                assertEquals("the movement written at v1 should survive", 1, transactions.size)
                assertEquals(2_550L, transactions.single().amountMinor)

                // The new tables exist and are usable, which is the point of the migration.
                val holdingId =
                    database.holdingDao().insert(
                        holding(accountId = accounts.single().id, symbol = "NVDA", assetClass = AssetClass.STOCK),
                    )
                database.tradeDao().insert(trade(holdingId = holdingId))
                assertEquals(1, database.holdingDao().countTrades(holdingId))
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 2 database keeps its money and gains the transfer column`() =
        runTest {
            createVersion(2)

            val database = openWithMigrations()
            try {
                val transactions = database.transactionDao().observeAll().first()
                assertEquals("the movement written at v2 should survive", 1, transactions.size)
                assertEquals(2_550L, transactions.single().amountMinor)
                // What a movement recorded before transfers existed is: an ordinary expense,
                // and not half of anything.
                assertNull(
                    "a movement from before transfers belongs to no transfer",
                    transactions.single().transferId,
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun `a transfer can be written into a migrated database`() =
        runTest {
            createVersion(2)

            val database = openWithMigrations()
            try {
                val dao = database.transactionDao()
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                val second =
                    database.accountDao().insert(
                        account(name = "Savings"),
                    )
                val outId =
                    dao.insert(
                        transaction(accountId = accountId, kind = TransactionKind.EXPENSE, amountMinor = 50_000),
                    )
                dao.setTransferId(id = outId, transferId = outId, updatedAt = FIXED_INSTANT)
                dao.insert(
                    transaction(
                        accountId = second,
                        kind = TransactionKind.INCOME,
                        amountMinor = 50_000,
                        transferId = outId,
                    ),
                )

                // The index the migration creates is what this reads through, so a column
                // added but not indexed would still pass — and a missing index would have
                // failed the schema check above anyway.
                assertEquals(2, dao.findTransferLegs(outId).size)
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 3 database keeps its money and gains the trade column`() =
        runTest {
            createVersion(3)

            val database = openWithMigrations()
            try {
                val transactions = database.transactionDao().observeAll().first()
                assertEquals("the movement written at v3 should survive", 1, transactions.size)
                assertEquals(2_550L, transactions.single().amountMinor)
                assertNull(
                    "a movement from before investments spent anything paid for no trade",
                    transactions.single().tradeId,
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun `the cash a purchase takes out can be written into a migrated database`() =
        runTest {
            createVersion(3)

            val database = openWithMigrations()
            try {
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                val holdingId = database.holdingDao().insert(holding(accountId, symbol = "NVD.DE"))
                val tradeId = database.tradeDao().insert(trade(holdingId))

                // `ALTER TABLE ... ADD COLUMN ... REFERENCES` is the only form SQLite accepts
                // for a constraint on an existing table, so this is where it is proved that the
                // reference really arrived rather than the column landing bare.
                val movementId =
                    database.transactionDao().insert(
                        transaction(accountId = accountId, amountMinor = 10_000, tradeId = tradeId),
                    )
                assertEquals(tradeId, database.transactionDao().findById(movementId)?.tradeId)
                assertThrows(SQLiteConstraintException::class.java) {
                    runBlocking { database.tradeDao().deleteById(tradeId) }
                }
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 4 database keeps its money and gains the recurring rules`() =
        runTest {
            createVersion(4)

            val database = openWithMigrations()
            try {
                val transactions = database.transactionDao().observeAll().first()
                assertEquals("the movement written at v4 should survive", 1, transactions.size)
                assertEquals(2_550L, transactions.single().amountMinor)
                assertNull(
                    "a movement from before rules existed was typed, not generated",
                    transactions.single().ruleId,
                )
                assertTrue(
                    database
                        .recurringRuleDao()
                        .observeAll()
                        .first()
                        .isEmpty(),
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun `deleting a rule leaves the movements it made, in a migrated database`() =
        runTest {
            createVersion(4)

            val database = openWithMigrations()
            try {
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                val ruleId =
                    database.recurringRuleDao().insert(
                        recurringRule(accountId = accountId),
                    )
                val movementId =
                    database.transactionDao().insert(
                        transaction(accountId = accountId, amountMinor = 1_299, ruleId = ruleId),
                    )

                // `SET NULL` is rule 8 written into the schema, and a column added by ALTER
                // TABLE is exactly where a constraint can silently fail to arrive.
                database.recurringRuleDao().delete(
                    database.recurringRuleDao().findById(ruleId)!!,
                )

                val survivor = database.transactionDao().findById(movementId)
                assertEquals(1_299L, survivor?.amountMinor)
                assertNull("the history stands, orphaned rather than deleted", survivor?.ruleId)
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 5 database keeps its rules and gains somewhere to put reminders`() =
        runTest {
            createVersion(5)

            val database = openWithMigrations()
            try {
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                val ruleId = database.recurringRuleDao().insert(recurringRule(accountId = accountId))

                database.ruleReminderDao().insert(RuleReminderEntity(ruleId = ruleId, offsetDays = -3))

                assertEquals(listOf(-3), database.ruleReminderDao().forRule(ruleId).map { it.offsetDays })
                assertEquals("the movement written at v5 should survive", 2_550L, moneyIn(database))
            } finally {
                database.close()
            }
        }

    @Test
    fun `deleting a rule takes its reminders and leaves its movements`() =
        runTest {
            createVersion(5)

            val database = openWithMigrations()
            try {
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                val ruleId = database.recurringRuleDao().insert(recurringRule(accountId = accountId))
                database.ruleReminderDao().insert(RuleReminderEntity(ruleId = ruleId, offsetDays = -3))
                val movementId =
                    database.transactionDao().insert(
                        transaction(accountId = accountId, amountMinor = 1_299, ruleId = ruleId),
                    )

                database.recurringRuleDao().delete(database.recurringRuleDao().findById(ruleId)!!)

                // The two keys pull in opposite directions on purpose, and both have to arrive
                // through the migration: a reminder is configuration and goes (`CASCADE`), a
                // movement is history and stays (`SET NULL`, rule 8).
                assertTrue(database.ruleReminderDao().forRule(ruleId).isEmpty())
                assertEquals(1_299L, database.transactionDao().findById(movementId)?.amountMinor)
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 6 database keeps its money and gains somewhere to put budgets`() =
        runTest {
            createVersion(6)

            val database = openWithMigrations()
            try {
                val categoryId = database.categoryDao().insert(category(name = "Groceries"))

                database.budgetDao().insert(budget(categoryId, amountMinor = 40_000))

                assertEquals(40_000L, database.budgetDao().findByCategory(categoryId)?.amountMinor)
                assertEquals("the movement written at v6 should survive", 2_550L, moneyIn(database))
            } finally {
                database.close()
            }
        }

    @Test
    fun `a category has one budget, and deleting the category takes it`() =
        runTest {
            createVersion(6)

            val database = openWithMigrations()
            try {
                val categoryId = database.categoryDao().insert(category(name = "Eating out"))
                database.budgetDao().insert(budget(categoryId, amountMinor = 15_000))

                // Both have to arrive through the migration: the unique index, and the cascade.
                assertThrows(SQLiteConstraintException::class.java) {
                    runBlocking { database.budgetDao().insert(budget(categoryId, amountMinor = 20_000)) }
                }
                database.categoryDao().deleteById(categoryId)
                assertNull(database.budgetDao().findByCategory(categoryId))
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 7 database keeps its money and gains groups`() =
        runTest {
            createVersion(7)

            val database = openWithMigrations()
            try {
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                val groupId = database.groupDao().insert(group(name = "Lisbon trip"))
                database.transactionDao().insert(
                    transaction(accountId = accountId, amountMinor = 4_200, groupId = groupId),
                )

                val lisbon =
                    database
                        .groupDao()
                        .observeTotals()
                        .first()
                        .single()
                assertEquals(4_200L, lisbon.expenseMinor)
                assertEquals(
                    "the movement written at v7 belongs to no group, so only one counts",
                    1,
                    lisbon.movementCount,
                )
                assertEquals("the movement written at v7 should survive", 2_550L, moneyIn(database))
            } finally {
                database.close()
            }
        }

    @Test
    fun `deleting a group leaves its movements, in a migrated database`() =
        runTest {
            createVersion(7)

            val database = openWithMigrations()
            try {
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                val groupId = database.groupDao().insert(group())
                val movementId =
                    database.transactionDao().insert(
                        transaction(accountId = accountId, amountMinor = 4_200, groupId = groupId),
                    )

                database.groupDao().deleteById(groupId)

                // `SET NULL` arriving through ALTER TABLE, which is where a constraint can
                // silently fail to arrive: a group is a way of reading movements, not their owner.
                val survivor = database.transactionDao().findById(movementId)
                assertEquals(4_200L, survivor?.amountMinor)
                assertNull(survivor?.groupId)
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 8 database keeps its rules and gains somewhere to put a postponement`() =
        runTest {
            createVersion(8)

            val database = openWithMigrations()
            try {
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                val ruleId = database.recurringRuleDao().insert(recurringRule(accountId = accountId))
                val moved =
                    RulePostponementEntity(
                        ruleId = ruleId,
                        occurrence = LocalDate.of(2026, 10, 15),
                        postponedTo = LocalDate.of(2026, 10, 18),
                    )
                database.rulePostponementDao().upsert(moved)

                assertEquals(listOf(moved), database.rulePostponementDao().all())
                assertEquals("the movement written at v8 should survive", 2_550L, moneyIn(database))

                // `CASCADE` arriving through the migration: a postponement is the rule's
                // configuration, and goes with it.
                database.recurringRuleDao().delete(database.recurringRuleDao().findById(ruleId)!!)
                assertTrue(database.rulePostponementDao().all().isEmpty())
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 9 database keeps its balances and gains somewhere to correct them`() =
        runTest {
            createVersion(9)

            val database = openWithMigrations()
            try {
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                assertEquals("the money written at v9 should survive", 2_550L, moneyIn(database))
                assertEquals(
                    "with no corrections the balance is what v9 showed",
                    10_000L - 2_550L,
                    database.accountDao().balanceOf(accountId),
                )

                database.balanceAdjustmentDao().insert(
                    BalanceAdjustmentEntity(
                        accountId = accountId,
                        amountMinor = 1_000,
                        currency = "EUR",
                        date = LocalDate.of(2026, 10, 5),
                        createdAt = Instant.parse("2026-10-05T10:00:00Z"),
                    ),
                )
                assertEquals(10_000L - 2_550L + 1_000L, database.accountDao().balanceOf(accountId))

                // `RESTRICT` arriving through the migration: an account's removal decides what
                // becomes of its corrections, so the database refuses to decide it alone.
                assertThrows(SQLiteConstraintException::class.java) {
                    runBlocking { database.accountDao().deleteById(accountId) }
                }
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 10 database keeps its money and gains somewhere to remember a matched charge`() =
        runTest {
            createVersion(10)

            val database = openWithMigrations()
            try {
                val accountId =
                    database
                        .accountDao()
                        .observeAll()
                        .first()
                        .single()
                        .id
                assertEquals("the money written at v10 should survive", 2_550L, moneyIn(database))
                val chargeId = database.transactionDao().insert(transaction(accountId = accountId, amountMinor = 1_299))
                val matchId =
                    database.captureMatchDao().insert(
                        CaptureMatchEntity(
                            chargeTransactionId = chargeId,
                            wroteCharge = false,
                            ruleId = null,
                            occurrence = null,
                            previousLastGenerated = null,
                            postponedTo = null,
                            accountId = accountId,
                            kind = TransactionKind.EXPENSE,
                            amountMinor = 1_299,
                            currency = "EUR",
                            date = LocalDate.of(2026, 10, 12),
                            note = "Compra en NETFLIX por 12,99 €",
                            matchedAt = Instant.parse("2026-10-12T10:00:00Z"),
                        ),
                    )

                // No key on the charge: deleting it leaves the match pointing where it did, so a
                // movement put back by its Undo is still the charge it was.
                database.transactionDao().deleteById(chargeId)

                assertEquals(chargeId, database.captureMatchDao().findById(matchId)?.chargeTransactionId)
                // `CASCADE` on the account: a match is about the account's captures, and goes with it.
                database.transactionDao().deleteAll()
                database.accountDao().deleteById(accountId)
                assertNull(database.captureMatchDao().findById(matchId))
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 11 card, savings or other account comes through as a bank account, money and all`() =
        runTest {
            createVersion(11)
            writeRaw(
                "INSERT INTO accounts " +
                    "(id, name, type, currency, openingBalanceMinor, colorArgb, archived, sortOrder, createdAt) " +
                    "VALUES (2, 'Visa', 'CARD', 'EUR', -20000, 0, 0, 1, 1755511200000), " +
                    "(3, 'Savings', 'SAVINGS', 'EUR', 500000, 0, 1, 2, 1755511200000), " +
                    "(4, 'Piggy bank', 'OTHER', 'EUR', 3000, 0, 0, 3, 1755511200000), " +
                    "(5, 'Wallet', 'CASH', 'EUR', 4000, 0, 0, 4, 1755511200000)",
                "INSERT INTO transactions " +
                    "(accountId, categoryId, kind, amountMinor, currency, date, createdAt, updatedAt) " +
                    "VALUES (2, NULL, 'EXPENSE', 1299, 'EUR', 20684, 1755511200000, 1755511200000)",
            )

            val database = openWithMigrations()
            try {
                // Read as stored, not through the converter, so it is the migration that is tested.
                assertEquals(
                    mapOf(
                        "Checking" to "BANK",
                        "Visa" to "BANK",
                        "Savings" to "BANK",
                        "Piggy bank" to "BANK",
                        "Wallet" to "CASH",
                    ),
                    storedTypes(database),
                )
                // And the app reads every one of them.
                val accounts = database.accountDao().observeAll().first()
                assertEquals(AccountType.CASH, accounts.single { it.name == "Wallet" }.type)
                // Only the name of the type moves: the same account, archived as it was, with
                // the same movements behind the same balance.
                assertEquals(listOf(1L, 2L, 3L, 4L, 5L), accounts.map { it.id }.sorted())
                assertTrue(accounts.single { it.name == "Savings" }.archived)
                assertEquals(-21_299L, database.accountDao().balanceOf(2))
                assertEquals(500_000L, database.accountDao().balanceOf(3))
                assertEquals("the money written at v11 should survive", 7_450L, database.accountDao().balanceOf(1))
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 12 group comes through on offer, and can then be archived`() =
        runTest {
            createVersion(12)
            writeRaw(
                "INSERT INTO movement_groups (id, name, startDate, endDate, createdAt) " +
                    "VALUES (1, 'June trip', 20615, 20622, 1755511200000)",
                "UPDATE transactions SET groupId = 1",
            )

            val database = openWithMigrations()
            try {
                val june = database.groupDao().findById(1)!!
                assertEquals("June trip", june.name)
                assertFalse("every group made before stays on offer", june.archived)
                assertEquals("the money written at v12 should survive", 2_550L, moneyIn(database))

                database.groupDao().update(june.copy(archived = true))

                assertTrue(database.groupDao().findById(1)!!.archived)
                val movement =
                    database
                        .transactionDao()
                        .observeAll()
                        .first()
                        .single()
                assertEquals("archiving moves no movement out of its group", 1L, movement.groupId)
            } finally {
                database.close()
            }
        }

    @Test
    fun `the migrated schema is one Room agrees to open`() =
        runTest {
            createVersion(1)

            // Opening is the assertion: Room validates every table, column and index against
            // the schema it expects for the current version, and throws if the migrations
            // produced anything else. Reading from a new table forces that check to happen
            // here.
            val database = openWithMigrations()
            try {
                assertTrue(
                    database
                        .holdingDao()
                        .observeAll()
                        .first()
                        .isEmpty(),
                )
                assertTrue(
                    database
                        .priceQuoteDao()
                        .observeAll()
                        .first()
                        .isEmpty(),
                )
            } finally {
                database.close()
            }
        }

    /** Each account's type as the database holds it, by name. */
    private fun storedTypes(database: CentsDatabase): Map<String, String> =
        database.openHelper.readableDatabase.query("SELECT name, type FROM accounts").use { rows ->
            buildMap { while (rows.moveToNext()) put(rows.getString(0), rows.getString(1)) }
        }

    private fun writeRaw(vararg statements: String) = old.writeRaw(*statements)

    private fun openWithMigrations(): CentsDatabase = old.openWithMigrations()

    private fun createVersion(version: Int) = old.createVersion(version)
}

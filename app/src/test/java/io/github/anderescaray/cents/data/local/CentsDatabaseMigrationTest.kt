package io.github.anderescaray.cents.data.local

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.data.local.entity.RuleReminderEntity
import io.github.anderescaray.cents.data.local.migration.ALL_MIGRATIONS
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

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
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        databaseFile = context.getDatabasePath(TEST_DATABASE)
        databaseFile.parentFile?.mkdirs()
        databaseFile.delete()
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

    private suspend fun moneyIn(database: CentsDatabase): Long =
        database
            .transactionDao()
            .observeAll()
            .first()
            .first { it.ruleId == null }
            .amountMinor

    private fun openWithMigrations(): CentsDatabase =
        Room
            .databaseBuilder(
                ApplicationProvider.getApplicationContext(),
                CentsDatabase::class.java,
                TEST_DATABASE,
            ).addMigrations(*ALL_MIGRATIONS)
            .build()

    /**
     * A real database at [version], created from the committed schema Room exported for it.
     *
     * Typing the old DDL out here would test this file against itself: the version that
     * shipped is the one Room exported, so that is the one a migration has to be able to
     * start from. The same account and movement go into every version, so what survives is
     * compared against the same numbers whichever hop is under test.
     */
    private fun createVersion(version: Int) {
        val schema =
            JSONObject(File(SCHEMA_DIRECTORY, "$version.json").readText()).getJSONObject("database")
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                database.execSQL(entity.getString("createSql").replace(TABLE_PLACEHOLDER, table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (i in 0 until indices.length()) {
                    database.execSQL(
                        indices.getJSONObject(i).getString("createSql").replace(TABLE_PLACEHOLDER, table),
                    )
                }
            }
            // Room's own bookkeeping: without it the database does not look like one Room
            // wrote, and the migration would be tested against something that never existed.
            database.execSQL(ROOM_MASTER_TABLE)
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, ?)",
                arrayOf(schema.getString("identityHash")),
            )
            database.execSQL(
                "INSERT INTO accounts " +
                    "(name, type, currency, openingBalanceMinor, colorArgb, archived, sortOrder, createdAt) " +
                    "VALUES ('Checking', 'BANK', 'EUR', 10000, 0, 0, 0, 1755511200000)",
            )
            database.execSQL(
                "INSERT INTO transactions " +
                    "(accountId, categoryId, kind, amountMinor, currency, date, createdAt, updatedAt) " +
                    "VALUES (1, NULL, 'EXPENSE', 2550, 'EUR', 20684, 1755511200000, 1755511200000)",
            )
            database.version = version
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"

        /** The exported DDL names its own table with this, backticks already around it. */
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
        val SCHEMA_DIRECTORY = File("schemas/${CentsDatabase::class.qualifiedName}")

        const val ROOM_MASTER_TABLE =
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
    }
}

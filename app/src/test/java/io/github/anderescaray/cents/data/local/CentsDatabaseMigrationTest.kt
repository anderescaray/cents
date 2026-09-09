package io.github.anderescaray.cents.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.data.local.migration.MIGRATION_1_2
import io.github.anderescaray.cents.domain.model.AssetClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The first migration test in this project, and the shape the rest follow.
 *
 * **What it actually proves.** A v1 database is built from the *exported* `1.json` rather than
 * from hand-written SQL — so it is the schema that really shipped — rows are put in it, and
 * then Room is opened on it with [MIGRATION_1_2]. Two things then have to hold, and the second
 * is the one that catches a typo in a migration:
 *
 * 1. **Nothing already recorded is lost.** The account and the movement written at v1 are still
 *    there afterwards, with the same values.
 * 2. **Room accepts the result.** After running a migration, Room compares the database against
 *    the schema it expects and refuses to open one that differs by so much as an index. So a
 *    migration that creates a column of the wrong type, or forgets an index, fails here rather
 *    than on the owner's phone — where it would mean an app that cannot start.
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
            createVersionOne()

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
    fun `the migrated schema is one Room agrees to open`() =
        runTest {
            createVersionOne()

            // Opening is the assertion: Room validates every table, column and index against
            // the schema it expects for version 2, and throws if the migration produced
            // anything else. Reading from a new table forces that check to happen here.
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

    private fun openWithMigrations(): CentsDatabase =
        Room
            .databaseBuilder(
                ApplicationProvider.getApplicationContext(),
                CentsDatabase::class.java,
                TEST_DATABASE,
            ).addMigrations(MIGRATION_1_2)
            .build()

    /**
     * A real version 1 database, created from the committed `1.json`.
     *
     * Typing the old DDL out here would test this file against itself: the version that
     * shipped is the one Room exported, so that is the one the migration has to be able to
     * start from.
     */
    private fun createVersionOne() {
        val schema = JSONObject(File(SCHEMA_DIRECTORY, "1.json").readText()).getJSONObject("database")
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
            database.version = 1
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

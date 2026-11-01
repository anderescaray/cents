package io.github.anderescaray.cents.data.local

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v13 to v14, which rebuilds `budgets` so a budget can be on no category: the general budget. A
 * rebuilt table is where an index or a cascade goes missing, so both are checked again here.
 */
@RunWith(RobolectricTestRunner::class)
class GeneralBudgetMigrationTest {
    private lateinit var old: MigrationTestDatabase

    @Before
    fun setUp() {
        old = MigrationTestDatabase()
    }

    @Test
    fun `a version 13 budget comes through on its category, and a general one can then be set`() =
        runTest {
            old.createVersion(13)
            old.writeRaw(
                "INSERT INTO categories (id, name, kind, colorArgb, archived, sortOrder, createdAt) " +
                    "VALUES (7, 'Groceries', 'EXPENSE', 0, 0, 0, 1755511200000)",
                "INSERT INTO budgets (id, categoryId, amountMinor, currency, createdAt) " +
                    "VALUES (3, 7, 40000, 'EUR', 1755511200000)",
            )

            val database = old.openWithMigrations()
            try {
                val groceries = database.budgetDao().findByCategory(7)!!
                assertEquals(3L, groceries.id)
                assertEquals(40_000L, groceries.amountMinor)
                assertEquals("EUR", groceries.currency)
                assertNull("no budget becomes general on its own", database.budgetDao().findGeneral())
                assertEquals("the money written at v13 should survive", 2_550L, moneyIn(database))

                database.budgetDao().insert(budget(null, amountMinor = 150_000))

                assertEquals(150_000L, database.budgetDao().findGeneral()?.amountMinor)
                // The index and the cascade are rebuilt with the table.
                assertThrows(SQLiteConstraintException::class.java) {
                    runBlocking { database.budgetDao().insert(budget(7, amountMinor = 20_000)) }
                }
                database.categoryDao().deleteById(7)
                assertNull(database.budgetDao().findByCategory(7))
                assertEquals(150_000L, database.budgetDao().findGeneral()?.amountMinor)
            } finally {
                database.close()
            }
        }
}

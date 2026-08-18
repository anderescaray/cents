package io.github.anderescaray.cents.data.local

import android.database.sqlite.SQLiteConstraintException
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TransactionDaoTest {
    private lateinit var database: CentsDatabase

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `date range query includes both boundaries`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, date = LocalDate.of(2026, 7, 31)),
                    transaction(accountId, date = LocalDate.of(2026, 8, 1)),
                    transaction(accountId, date = LocalDate.of(2026, 8, 31)),
                    transaction(accountId, date = LocalDate.of(2026, 9, 1)),
                ),
            )

            val august = dao.observeBetween(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).first()

            assertEquals(
                listOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)),
                august.map { it.date },
            )
        }

    @Test
    fun `transactions without a category land in the uncategorized inbox`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val categoryId = database.categoryDao().insert(category())
            val dao = database.transactionDao()
            dao.insert(transaction(accountId, categoryId = categoryId))
            val orphan = dao.insert(transaction(accountId, categoryId = null))

            assertEquals(listOf(orphan), dao.observeUncategorized().first().map { it.id })
        }

    @Test
    fun `an account with transactions cannot be deleted`() =
        runTest {
            val accountDao = database.accountDao()
            val accountId = accountDao.insert(account())
            database.transactionDao().insert(transaction(accountId))
            val stored = requireNotNull(accountDao.findById(accountId))

            // Deleting would orphan history; the account is archived instead.
            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { accountDao.delete(stored) }
            }
        }

    @Test
    fun `a category with transactions cannot be deleted`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val categoryDao = database.categoryDao()
            val categoryId = categoryDao.insert(category())
            database.transactionDao().insert(transaction(accountId, categoryId = categoryId))
            val stored = requireNotNull(categoryDao.findById(categoryId))

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { categoryDao.delete(stored) }
            }
        }

    @Test
    fun `the transaction list is ordered by date descending`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, date = LocalDate.of(2026, 1, 15)),
                    transaction(accountId, date = LocalDate.of(2026, 8, 2)),
                    transaction(accountId, date = LocalDate.of(2026, 3, 9)),
                ),
            )

            assertEquals(
                listOf(
                    LocalDate.of(2026, 8, 2),
                    LocalDate.of(2026, 3, 9),
                    LocalDate.of(2026, 1, 15),
                ),
                dao.observeAll().first().map { it.date },
            )
        }

    @Test
    fun `amount and kind survive the round trip unchanged`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val dao = database.transactionDao()
            val id =
                dao.insert(
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 123_456, currency = "USD"),
                )

            val stored = requireNotNull(dao.findById(id))

            assertEquals(TransactionKind.INCOME, stored.kind)
            assertEquals(123_456L, stored.amountMinor)
            assertEquals("USD", stored.currency)
        }
}

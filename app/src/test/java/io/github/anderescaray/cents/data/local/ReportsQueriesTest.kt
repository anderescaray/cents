package io.github.anderescaray.cents.data.local

import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The aggregation the charts read. Every assertion here is about SQL doing the adding up,
 * because the alternative — summing in Kotlin — is correct and gets slower forever.
 */
@RunWith(RobolectricTestRunner::class)
class ReportsQueriesTest {
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
    fun `spending by category sums each category and orders by size`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val rent = database.categoryDao().insert(category(name = "Rent"))
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, groceries, amountMinor = 1_500, date = LocalDate.of(2026, 8, 3)),
                    transaction(accountId, groceries, amountMinor = 2_500, date = LocalDate.of(2026, 8, 7)),
                    transaction(accountId, rent, amountMinor = 90_000, date = LocalDate.of(2026, 8, 1)),
                ),
            )

            val rows = dao.spendingIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

            assertEquals(listOf("Rent", "Groceries"), rows.map { it.categoryName })
            assertEquals(listOf(90_000L, 4_000L), rows.map { it.totalMinor })
            assertEquals(listOf(1, 2), rows.map { it.transactionCount })
        }

    @Test
    fun `uncategorized spending is its own slice rather than a dropped row`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, groceries, amountMinor = 1_000),
                    transaction(accountId, categoryId = null, amountMinor = 3_000),
                ),
            )

            val rows = dao.spendingIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

            assertEquals(2, rows.size)
            val unnamed = rows.single { it.categoryId == null }
            assertNull(unnamed.categoryName)
            assertEquals(3_000L, unnamed.totalMinor)
        }

    @Test
    fun `spending by category ignores the other kind and dates outside the period`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val salary = database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, groceries, amountMinor = 1_000, date = LocalDate.of(2026, 8, 15)),
                    transaction(accountId, groceries, amountMinor = 5_000, date = LocalDate.of(2026, 7, 31)),
                    transaction(
                        accountId,
                        salary,
                        kind = TransactionKind.INCOME,
                        amountMinor = 200_000,
                        date = LocalDate.of(2026, 8, 15),
                    ),
                ),
            )

            val expenses = dao.spendingIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

            assertEquals(1, expenses.size)
            assertEquals(1_000L, expenses.single().totalMinor)
        }

    @Test
    fun `the period boundaries are both included`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val categoryId = database.categoryDao().insert(category())
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, categoryId, amountMinor = 100, date = LocalDate.of(2026, 8, 1)),
                    transaction(accountId, categoryId, amountMinor = 200, date = LocalDate.of(2026, 8, 31)),
                ),
            )

            val rows = dao.spendingIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

            assertEquals(300L, rows.single().totalMinor)
        }

    @Test
    fun `monthly totals split income from expenses and key each month correctly`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(
                        accountId,
                        kind = TransactionKind.INCOME,
                        amountMinor = 200_000,
                        date = LocalDate.of(2026, 6, 30),
                    ),
                    transaction(accountId, amountMinor = 45_000, date = LocalDate.of(2026, 6, 30)),
                    transaction(accountId, amountMinor = 12_000, date = LocalDate.of(2026, 8, 1)),
                ),
            )

            val rows = dao.observeMonthlyTotals(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), null).first()

            // July is absent: SQL groups what exists, and the gap is filled in the repository.
            assertEquals(listOf("2026-06", "2026-08"), rows.map { it.month })
            assertEquals(listOf(200_000L, 0L), rows.map { it.incomeMinor })
            assertEquals(listOf(45_000L, 12_000L), rows.map { it.expenseMinor })
        }

    @Test
    fun `the month key is the calendar month, including on a leap day`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val dao = database.transactionDao()
            // A date on the last day of a month is where an off-by-one in the
            // epoch-day-to-timestamp conversion would surface as the wrong month.
            dao.insertAll(
                listOf(
                    transaction(accountId, amountMinor = 1, date = LocalDate.of(2026, 1, 31)),
                    transaction(accountId, amountMinor = 2, date = LocalDate.of(2026, 2, 1)),
                    transaction(accountId, amountMinor = 3, date = LocalDate.of(2024, 2, 29)),
                ),
            )

            val rows = dao.observeMonthlyTotals(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31), null).first()

            assertEquals(listOf("2024-02", "2026-01", "2026-02"), rows.map { it.month })
            assertEquals(listOf(3L, 1L, 2L), rows.map { it.expenseMinor })
        }
}

private suspend fun io.github.anderescaray.cents.data.local.dao.TransactionDao.spendingIn(
    from: LocalDate,
    to: LocalDate,
) = observeTotalsByCategory(TransactionKind.EXPENSE, from, to, null).first()

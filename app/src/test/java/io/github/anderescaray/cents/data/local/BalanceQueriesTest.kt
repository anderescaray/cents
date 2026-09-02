package io.github.anderescaray.cents.data.local

import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The two queries the balance line is made of, tested against SQLite rather than against the
 * Kotlin that walks their results.
 *
 * `ReportsQueriesTest` beside this file covers the donut's and the columns' queries; this one
 * covers the balance line's, one file per view the way the rest of the tab is laid out.
 */
@RunWith(RobolectricTestRunner::class)
class BalanceQueriesTest {
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
    fun `the daily net signs income up and expenses down, one row per day`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 200_000, date = august(1)),
                    transaction(accountId, amountMinor = 45_000, date = august(1)),
                    transaction(accountId, amountMinor = 12_000, date = august(4)),
                ),
            )

            val rows = dao.observeDailyNet(august(1), august(31)).first()

            assertEquals(listOf(august(1), august(4)), rows.map { it.date })
            assertEquals(listOf(155_000L, -12_000L), rows.map { it.netMinor })
            // The count is movements, not days: it is what separates "nothing happened" from
            // "it happened and cancelled out".
            assertEquals(listOf(2, 1), rows.map { it.movementCount })
        }

    @Test
    fun `a day whose movements cancel out is still a row`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 7_000, date = august(9)),
                    transaction(accountId, amountMinor = 7_000, date = august(9)),
                ),
            )

            val rows = dao.observeDailyNet(august(1), august(31)).first()

            assertEquals(1, rows.size)
            assertEquals(0L, rows.single().netMinor)
            assertEquals(2, rows.single().movementCount)
        }

    @Test
    fun `the daily net ignores dates outside the period, both ends included`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, amountMinor = 100, date = LocalDate.of(2026, 7, 31)),
                    transaction(accountId, amountMinor = 200, date = august(1)),
                    transaction(accountId, amountMinor = 300, date = august(31)),
                    transaction(accountId, amountMinor = 400, date = LocalDate.of(2026, 9, 1)),
                ),
            )

            val rows = dao.observeDailyNet(august(1), august(31)).first()

            assertEquals(listOf(august(1), august(31)), rows.map { it.date })
            assertEquals(listOf(-200L, -300L), rows.map { it.netMinor })
        }

    @Test
    fun `the balance before a period is opening balances plus everything strictly earlier`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
            val card = database.accountDao().insert(account(name = "Card", openingBalanceMinor = -25_000))
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(
                        checking,
                        kind = TransactionKind.INCOME,
                        amountMinor = 60_000,
                        date = LocalDate.of(2026, 7, 5),
                    ),
                    transaction(card, amountMinor = 15_000, date = LocalDate.of(2026, 7, 31)),
                    // On the first day of the period: inside the line, not behind it.
                    transaction(checking, amountMinor = 9_999, date = august(1)),
                ),
            )

            val before = dao.observeBalanceBefore(august(1)).first()

            assertEquals(100_000L - 25_000L + 60_000L - 15_000L, before)
        }

    @Test
    fun `an account that has never been used still contributes its opening balance`() =
        runTest {
            database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 500_000))

            val before = database.transactionDao().observeBalanceBefore(august(1)).first()

            assertEquals(500_000L, before)
        }

    @Test
    fun `no accounts and no movements is zero rather than null`() =
        runTest {
            val before = database.transactionDao().observeBalanceBefore(august(1)).first()

            assertEquals(0L, before)
        }

    @Test
    fun `the balance before a period agrees with the account balances at that point`() =
        runTest {
            // The same arithmetic AccountDao.observeBalances does, cut off at a date. If the
            // two ever disagree, the chart and the Accounts tab disagree about the same money.
            val accountId = database.accountDao().insert(account(openingBalanceMinor = 30_000))
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 80_000, date = august(2)),
                    transaction(accountId, amountMinor = 11_000, date = august(3)),
                ),
            )

            val afterEverything = dao.observeBalanceBefore(LocalDate.of(2026, 9, 1)).first()
            val accountsTabTotal =
                database
                    .accountDao()
                    .observeBalances()
                    .first()
                    .sumOf { it.balanceMinor }

            assertEquals(accountsTabTotal, afterEverything)
            assertTrue(afterEverything > 0)
        }

    private fun august(day: Int) = LocalDate.of(2026, 8, day)
}

package io.github.anderescaray.cents.data.local

import io.github.anderescaray.cents.data.local.entity.BalanceAdjustmentEntity
import io.github.anderescaray.cents.domain.model.AccountType
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

            val rows = dao.observeDailyNet(august(1), august(31), null).first()

            assertEquals(listOf(august(1), august(4)), rows.map { it.date })
            assertEquals(listOf(155_000L, -12_000L), rows.map { it.netMinor })
            // The count is movements, not days: it is what separates "nothing happened" from
            // "it happened and cancelled out".
            assertEquals(listOf(2, 1), rows.map { it.movementCount })
        }

    @Test
    fun `over every account the line is cash and bank, and a broker counts only on its own`() =
        runTest {
            val bank = database.accountDao().insert(account(name = "Bank", openingBalanceMinor = 100_000))
            val broker =
                database.accountDao().insert(
                    account(name = "Broker", openingBalanceMinor = 50_000, type = AccountType.BROKER),
                )
            val dao = database.transactionDao()
            dao.insertAll(
                listOf(
                    transaction(bank, amountMinor = 2_799, date = august(1)),
                    // Shares bought with the broker's cash: invested, not spent.
                    transaction(broker, amountMinor = 30_000, date = august(2)),
                    transaction(broker, amountMinor = 5_000, date = LocalDate.of(2026, 7, 20)),
                ),
            )

            // The broker's opening balance and July purchase stay out of every-account's start...
            assertEquals(100_000L, dao.observeBalanceBefore(august(1), null).first())
            // ...and its August purchase out of every-account's days.
            assertEquals(listOf(-2_799L), dao.observeDailyNet(august(1), august(31), null).first().map { it.netMinor })

            // Chosen on its own, the broker is its own cash.
            assertEquals(45_000L, dao.observeBalanceBefore(august(1), broker).first())
            assertEquals(
                listOf(-30_000L),
                dao.observeDailyNet(august(1), august(31), broker).first().map { it.netMinor },
            )
        }

    @Test
    fun `all time starts at the first movement in cash and bank, not in a broker`() =
        runTest {
            val bank = database.accountDao().insert(account(name = "Bank"))
            val broker = database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))
            val dao = database.transactionDao()
            assertEquals(null, dao.observeFirstBalanceDate(null).first())

            dao.insertAll(
                listOf(
                    transaction(broker, date = LocalDate.of(2025, 1, 3)),
                    transaction(bank, date = LocalDate.of(2025, 6, 9)),
                    transaction(bank, date = august(1)),
                ),
            )

            assertEquals(LocalDate.of(2025, 6, 9), dao.observeFirstBalanceDate(null).first())
            assertEquals(LocalDate.of(2025, 1, 3), dao.observeFirstBalanceDate(broker).first())
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

            val rows = dao.observeDailyNet(august(1), august(31), null).first()

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

            val rows = dao.observeDailyNet(august(1), august(31), null).first()

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

            val before = dao.observeBalanceBefore(august(1), null).first()

            assertEquals(100_000L - 25_000L + 60_000L - 15_000L, before)
        }

    @Test
    fun `an account that has never been used still contributes its opening balance`() =
        runTest {
            database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 500_000))

            val before = database.transactionDao().observeBalanceBefore(august(1), null).first()

            assertEquals(500_000L, before)
        }

    @Test
    fun `no accounts and no movements is zero rather than null`() =
        runTest {
            val before = database.transactionDao().observeBalanceBefore(august(1), null).first()

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

            val afterEverything = dao.observeBalanceBefore(LocalDate.of(2026, 9, 1), null).first()
            val accountsTabTotal =
                database
                    .accountDao()
                    .observeBalances()
                    .first()
                    .sumOf { it.balanceMinor }

            assertEquals(accountsTabTotal, afterEverything)
            assertTrue(afterEverything > 0)
        }

    @Test
    fun `a balance correction is a step on its own day, for its own account`() =
        runTest {
            val mine = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 10_000))
            val other = database.accountDao().insert(account(name = "Cash"))
            val dao = database.transactionDao()
            dao.insert(transaction(mine, amountMinor = 3_000, date = august(4)))
            correction(mine, amountMinor = 1_250, date = august(10))
            correction(other, amountMinor = -400, date = august(10))

            val rows = dao.observeDailyNet(august(1), august(31), mine).first()

            assertEquals(listOf(august(4), august(10)), rows.map { it.date })
            assertEquals(listOf(-3_000L, 1_250L), rows.map { it.netMinor })
            // A period whose only change is a correction is not an empty one.
            assertEquals(listOf(1, 1), rows.map { it.movementCount })
            assertEquals(
                listOf(-3_000L, 850L),
                dao.observeDailyNet(august(1), august(31), null).first().map { it.netMinor },
            )
        }

    @Test
    fun `the balance before a day counts the corrections dated before it, and none after`() =
        runTest {
            val accountId = database.accountDao().insert(account(openingBalanceMinor = 10_000))
            correction(accountId, amountMinor = 1_250, date = august(10))

            val dao = database.transactionDao()
            assertEquals(10_000L, dao.observeBalanceBefore(august(10), accountId).first())
            assertEquals(11_250L, dao.observeBalanceBefore(august(11), accountId).first())
            assertEquals(11_250L, dao.observeBalanceBefore(august(11), null).first())
        }

    @Test
    fun `with corrections, the line still ends where the Accounts tab says the money is`() =
        runTest {
            val first = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 30_000))
            val second = database.accountDao().insert(account(name = "Cash", openingBalanceMinor = 2_000))
            database.transactionDao().insert(transaction(first, amountMinor = 11_000, date = august(3)))
            database.transactionDao().insert(transaction(second, amountMinor = 500, date = august(3)))
            database.transactionDao().insert(transaction(second, amountMinor = 500, date = august(4)))
            correction(first, amountMinor = -2_500, date = august(5))
            correction(second, amountMinor = 700, date = august(6))
            correction(second, amountMinor = 300, date = august(6))

            val afterEverything = database.transactionDao().observeBalanceBefore(LocalDate.of(2026, 9, 1), null).first()
            val balances =
                database
                    .accountDao()
                    .observeBalances()
                    .first()
                    .associate { it.accountId to it.balanceMinor }

            // Two movements and two corrections on one account: neither may be counted once per
            // row of the other, which a second join would do.
            assertEquals(mapOf(first to 16_500L, second to 2_000L), balances)
            assertEquals(balances.values.sum(), afterEverything)
            assertEquals(2_000L, database.accountDao().observeBalance(second).first())
        }

    private suspend fun correction(
        accountId: Long,
        amountMinor: Long,
        date: LocalDate,
    ) {
        database.balanceAdjustmentDao().insert(
            BalanceAdjustmentEntity(
                accountId = accountId,
                amountMinor = amountMinor,
                currency = "EUR",
                date = date,
                createdAt = FIXED_INSTANT,
            ),
        )
    }

    private fun august(day: Int) = LocalDate.of(2026, 8, day)
}

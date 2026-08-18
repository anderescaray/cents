package io.github.anderescaray.cents.data.local

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.entity.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountDaoTest {
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
    fun `balance of an account without movements is its opening balance`() =
        runTest {
            val id = database.accountDao().insert(account(openingBalanceMinor = 25_000))

            assertEquals(25_000L, database.accountDao().observeBalance(id).first())
        }

    @Test
    fun `income adds to the balance and expense subtracts`() =
        runTest {
            val dao = database.accountDao()
            val id = dao.insert(account(openingBalanceMinor = 10_000))

            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId = id, kind = TransactionKind.INCOME, amountMinor = 200_000),
                    transaction(accountId = id, kind = TransactionKind.EXPENSE, amountMinor = 4_599),
                    transaction(accountId = id, kind = TransactionKind.EXPENSE, amountMinor = 1_250),
                ),
            )

            // 10_000 + 200_000 - 4_599 - 1_250
            assertEquals(204_151L, dao.observeBalance(id).first())
        }

    @Test
    fun `each account keeps its own balance`() =
        runTest {
            val dao = database.accountDao()
            val checking = dao.insert(account(name = "Checking", openingBalanceMinor = 50_000))
            val cash = dao.insert(account(name = "Cash", openingBalanceMinor = 3_000))

            database.transactionDao().insert(
                transaction(accountId = checking, kind = TransactionKind.EXPENSE, amountMinor = 20_000),
            )

            val balances = dao.observeBalances().first().associateBy { it.accountId }
            assertEquals(30_000L, balances.getValue(checking).balanceMinor)
            assertEquals(3_000L, balances.getValue(cash).balanceMinor)
        }

    @Test
    fun `balance flow re-emits when a transaction is inserted`() =
        runTest {
            val dao = database.accountDao()
            val id = dao.insert(account(openingBalanceMinor = 0))

            dao.observeBalance(id).test {
                assertEquals(0L, awaitItem())

                database.transactionDao().insert(
                    transaction(accountId = id, kind = TransactionKind.INCOME, amountMinor = 7_777),
                )

                assertEquals(7_777L, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `amounts beyond double precision survive a round trip`() =
        runTest {
            // 2^53 + 1: the first integer a Double cannot represent. Stored as Long it must
            // come back byte-exact, which is the whole reason money is not a floating point.
            val hugeAmount = 9_007_199_254_740_993L
            val dao = database.accountDao()
            val id = dao.insert(account(openingBalanceMinor = 0))

            database.transactionDao().insert(
                transaction(accountId = id, kind = TransactionKind.INCOME, amountMinor = hugeAmount),
            )

            assertEquals(hugeAmount, dao.observeBalance(id).first())
        }

    @Test
    fun `archiving an account keeps it out of the active list but not out of the database`() =
        runTest {
            val dao = database.accountDao()
            val id = dao.insert(account(name = "Old card"))

            dao.setArchived(id, archived = true)

            assertEquals(emptyList<Long>(), dao.observeActive().first().map { it.id })
            assertEquals(listOf(id), dao.observeAll().first().map { it.id })
        }
}

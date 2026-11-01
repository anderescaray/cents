package io.github.anderescaray.cents.data.repository

import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.entity.BalanceAdjustmentEntity
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.BalanceChange
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Setting an account's balance, both ways, against a real database.
 *
 * What has to hold, from `docs/next-improvements.md` §7: the account ends at the typed balance
 * whichever way is chosen and however many times; the chosen way decides only what the earlier
 * balances do; and nothing about income, expenses, movements or investments changes.
 */
@RunWith(RobolectricTestRunner::class)
class RoomBalanceRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var balances: RoomBalanceRepository
    private lateinit var accounts: RoomAccountRepository
    private lateinit var transactions: RoomTransactionRepository

    private val clock = Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 5)

    @Before
    fun setUp() {
        database = createTestDatabase()
        balances = RoomBalanceRepository(database, clock)
        accounts = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock)
        transactions = RoomTransactionRepository(database.transactionDao(), database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** An account opened with 100 € that has since spent 30 €: 70 € by Cents' reckoning. */
    private suspend fun seventyEuros(type: AccountType = AccountType.BANK): Long {
        val id = database.accountDao().insert(account(openingBalanceMinor = 10_000, type = type))
        database.transactionDao().insert(transaction(id, amountMinor = 3_000, date = LocalDate.of(2026, 9, 10)))
        return id
    }

    private suspend fun balanceOf(id: Long): Long = requireNotNull(database.accountDao().balanceOf(id))

    private suspend fun openingOf(id: Long): Long =
        requireNotNull(database.accountDao().findById(id)).openingBalanceMinor

    /** The balance as the balance line has it at the start of [day]. */
    private suspend fun balanceBefore(
        day: LocalDate,
        id: Long,
    ): Long = database.transactionDao().observeBalanceBefore(day, id).first()

    @Test
    fun `a correction from today reaches the balance and leaves every earlier one alone`() =
        runTest {
            val id = seventyEuros()

            val applied = balances.setBalance(id, Money(8_250, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)

            assertEquals(Money(1_250, "EUR"), applied)
            assertEquals(8_250L, balanceOf(id))
            assertEquals("the opening balance is not touched", 10_000L, openingOf(id))
            assertEquals("yesterday's balance is what it was", 7_000L, balanceBefore(today, id))
            assertEquals(8_250L, balanceBefore(today.plusDays(1), id))
            val correction = balances.observeAdjustments(id).first().single()
            assertEquals(Money(1_250, "EUR"), correction.amount)
            assertEquals(today, correction.date)
        }

    @Test
    fun `rebasing moves the opening balance, and every earlier balance with it`() =
        runTest {
            val id = seventyEuros()

            val applied = balances.setBalance(id, Money(8_250, "EUR"), BalanceChange.REBASE_OPENING, on = today)

            assertEquals(Money(1_250, "EUR"), applied)
            assertEquals(8_250L, balanceOf(id))
            assertEquals(11_250L, openingOf(id))
            assertEquals("the whole history moved by the difference", 8_250L, balanceBefore(today, id))
            assertEquals(11_250L, balanceBefore(LocalDate.of(2026, 9, 1), id))
            assertTrue("no correction is left behind", balances.observeAdjustments(id).first().isEmpty())
        }

    @Test
    fun `zero and negative targets are reached both ways`() =
        runTest {
            for (change in BalanceChange.entries) {
                for (target in listOf(0L, -4_500L)) {
                    val id =
                        database.accountDao().insert(
                            account(name = "$change $target", openingBalanceMinor = 10_000),
                        )
                    database.transactionDao().insert(transaction(id, amountMinor = 3_000))

                    balances.setBalance(id, Money(target, "EUR"), change, on = today)

                    assertEquals("$change to $target", target, balanceOf(id))
                }
            }
        }

    @Test
    fun `setting the balance it already has writes nothing`() =
        runTest {
            val id = seventyEuros()

            val applied = balances.setBalance(id, Money(7_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)

            assertEquals(Money(0, "EUR"), applied)
            assertTrue(balances.observeAdjustments(id).first().isEmpty())
            assertEquals(10_000L, openingOf(id))
        }

    @Test
    fun `each change is measured from the balance earlier corrections already reached`() =
        runTest {
            val id = seventyEuros()

            balances.setBalance(id, Money(9_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)
            balances.setBalance(id, Money(8_000, "EUR"), BalanceChange.REBASE_OPENING, on = today)
            balances.setBalance(id, Money(8_500, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)

            assertEquals(8_500L, balanceOf(id))
            assertEquals(
                "+20 then +5, not +20 then +15",
                listOf(500L, 2_000L),
                balances.observeAdjustments(id).first().map { it.amount.minorUnits },
            )
            assertEquals("the rebase took 10 € off the opening", 9_000L, openingOf(id))
        }

    @Test
    fun `a payment captured after the screen read the balance is not undone`() =
        runTest {
            val id = seventyEuros()
            // The screen showed 70 €; then a notification recorded a 5 € coffee before Save.
            transactions.addTransaction(
                Transaction(
                    id = 0,
                    accountId = id,
                    categoryId = null,
                    kind = TransactionKind.EXPENSE,
                    amount = Money(500, "EUR"),
                    date = today,
                    note = "Coffee",
                ),
            )

            val applied = balances.setBalance(id, Money(8_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)

            assertEquals("measured against 65 €, not the 70 € on screen", Money(1_500, "EUR"), applied)
            assertEquals(8_000L, balanceOf(id))
        }

    @Test
    fun `movements after a correction still move the balance`() =
        runTest {
            val id = seventyEuros()
            balances.setBalance(id, Money(10_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)

            database.transactionDao().insert(transaction(id, amountMinor = 2_000, date = today.plusDays(2)))

            assertEquals(8_000L, balanceOf(id))
        }

    @Test
    fun `neither way is an income or an expense, and no movement changes`() =
        runTest {
            val id = seventyEuros()
            val groceries = database.categoryDao().insert(category(kind = CategoryKind.EXPENSE))
            database.transactionDao().insert(transaction(id, categoryId = groceries, amountMinor = 1_000, date = today))
            val movementsBefore = database.transactionDao().observeAll().first()
            val waitingBefore = database.transactionDao().observeUncategorizedCount().first()
            val october = today.withDayOfMonth(1) to today.withDayOfMonth(31)

            balances.setBalance(id, Money(50_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)
            balances.setBalance(id, Money(-20_000, "EUR"), BalanceChange.REBASE_OPENING, on = today)

            assertEquals(movementsBefore, database.transactionDao().observeAll().first())
            val summary = transactions.observeSummary(october.first, october.second, "EUR").first()
            assertEquals(Money(0, "EUR"), summary.income)
            assertEquals(Money(1_000, "EUR"), summary.expenses)
            val monthly = database.transactionDao().observeMonthlyTotals(october.first, october.second, null).first()
            assertEquals(listOf(0L to 1_000L), monthly.map { it.incomeMinor to it.expenseMinor })
            val byCategory =
                database
                    .transactionDao()
                    .observeTotalsByCategory(TransactionKind.EXPENSE, october.first, october.second, null)
                    .first()
            assertEquals(listOf(1_000L), byCategory.map { it.totalMinor })
            assertEquals(
                "nothing new waits to be categorized",
                waitingBefore,
                database.transactionDao().observeUncategorizedCount().first(),
            )
        }

    @Test
    fun `correcting broker cash leaves holdings and trades alone`() =
        runTest {
            val id = seventyEuros(type = AccountType.BROKER)
            val holdingId = database.holdingDao().insert(holding(accountId = id))
            database.tradeDao().insert(trade(holdingId = holdingId, amountMinor = 20_000))
            val tradesBefore = database.tradeDao().observeAll().first()
            val holdingsBefore = database.holdingDao().observeAll().first()

            balances.setBalance(id, Money(1_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)
            balances.setBalance(id, Money(2_000, "EUR"), BalanceChange.REBASE_OPENING, on = today)

            assertEquals(2_000L, balanceOf(id))
            assertEquals(tradesBefore, database.tradeDao().observeAll().first())
            assertEquals(holdingsBefore, database.holdingDao().observeAll().first())
        }

    @Test
    fun `a balance in another currency is refused, and a missing account is nothing`() =
        runTest {
            val id = seventyEuros()

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    balances.setBalance(id, Money(1_000, "USD"), BalanceChange.CORRECT_FROM_TODAY, on = today)
                }
            }
            assertNull(balances.setBalance(999, Money(1_000, "EUR"), BalanceChange.REBASE_OPENING, on = today))
            assertEquals(7_000L, balanceOf(id))
        }

    @Test
    fun `removing a correction puts the balance back`() =
        runTest {
            val id = seventyEuros()
            balances.setBalance(id, Money(9_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)

            balances.deleteAdjustment(
                balances
                    .observeAdjustments(id)
                    .first()
                    .single()
                    .id,
            )

            assertEquals(7_000L, balanceOf(id))
        }

    @Test
    fun `a removed correction can be put back as it was`() =
        runTest {
            val id = seventyEuros()
            balances.setBalance(id, Money(9_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)
            val before = database.balanceAdjustmentDao().all().single()

            val removed = requireNotNull(balances.deleteAdjustment(before.id))
            assertEquals(7_000L, balanceOf(id))
            assertNull("a second removal finds nothing", balances.deleteAdjustment(before.id))

            balances.restoreAdjustment(removed)

            assertEquals(9_000L, balanceOf(id))
            assertEquals(before.copy(createdAt = clock.instant()), database.balanceAdjustmentDao().all().single())
        }

    @Test
    fun `every screen's balance follows a correction as it is written`() =
        runTest {
            val id = seventyEuros()

            accounts.observeAccountsWithBalances().test {
                assertEquals(Money(7_000, "EUR"), awaitItem().single().balance)
                balances.setBalance(id, Money(9_900, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)
                assertEquals(Money(9_900, "EUR"), awaitItem().single().balance)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(Money(9_900, "EUR"), accounts.observeAccountWithBalance(id).first()?.balance)
        }

    @Test
    fun `an account holding only a correction is deleted with it`() =
        runTest {
            val id = database.accountDao().insert(account())
            balances.setBalance(id, Money(5_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)

            accounts.deleteAccount(id)

            assertNull(database.accountDao().findById(id))
            assertTrue(database.balanceAdjustmentDao().all().isEmpty())
        }

    @Test
    fun `moving an account's movements elsewhere moves its corrections with them`() =
        runTest {
            val from = seventyEuros()
            val to = database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 1_000))
            balances.setBalance(from, Money(9_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)

            accounts.reassignAndDelete(from, to)

            assertEquals(listOf(to), database.balanceAdjustmentDao().all().map { it.accountId })
            // Everything arrives: its 100 € opening balance, the -30 € movement and the +20 €
            // correction. The two balances are added together, 90 € and Savings' own 10 €.
            assertEquals(9_000L + 1_000L, balanceOf(to))
            assertEquals(10_000L + 1_000L, openingOf(to))
        }

    @Test
    fun `deleting an account with its movements deletes its corrections too`() =
        runTest {
            val id = seventyEuros()
            val other = database.accountDao().insert(account(name = "Savings"))
            balances.setBalance(id, Money(9_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)
            balances.setBalance(other, Money(4_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = today)

            accounts.deleteAccountWithTransactions(id)

            assertEquals(listOf(other), database.balanceAdjustmentDao().all().map { it.accountId })
            assertEquals(4_000L, balanceOf(other))
        }

    @Test
    fun `a correction cannot be left pointing at an account that is gone`() =
        runTest {
            val id = database.accountDao().insert(account())
            database.balanceAdjustmentDao().insert(
                BalanceAdjustmentEntity(
                    accountId = id,
                    amountMinor = 100,
                    currency = "EUR",
                    date = today,
                    createdAt = clock.instant(),
                ),
            )

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { database.accountDao().deleteById(id) }
            }
        }
}

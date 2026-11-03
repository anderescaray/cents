package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.Budget
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RoomBudgetRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var budgets: RoomBudgetRepository
    private val clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC)
    private val september = LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30)

    @Before
    fun setUp() {
        database = createTestDatabase()
        budgets = RoomBudgetRepository(database.budgetDao(), database.transactionDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a new budget is created, and saving it again changes its limit rather than adding one`() =
        runTest {
            val groceries = database.categoryDao().insert(category(name = "Groceries"))

            val id = budgets.saveBudget(Budget(Budget.NEW, groceries, Money(40_000, "EUR")))
            budgets.saveBudget(Budget(id, groceries, Money(35_000, "EUR")))

            val saved = budgets.observeBudgets().first().single()
            assertEquals(id, saved.id)
            assertEquals(Money(35_000, "EUR"), saved.limit)
        }

    @Test
    fun `spent is this month's expenses by category, and nothing that is not spending`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val september12 = LocalDate.of(2026, 9, 12)
            database.transactionDao().insert(
                transaction(checking, categoryId = groceries, amountMinor = 2_550, date = september12),
            )
            database.transactionDao().insert(
                transaction(checking, categoryId = groceries, amountMinor = 1_000, date = september12),
            )
            // Last month, income, and a trade's cash: none of it is this month's spending.
            database.transactionDao().insert(
                transaction(checking, categoryId = groceries, amountMinor = 9_999, date = LocalDate.of(2026, 8, 31)),
            )
            database.transactionDao().insert(
                transaction(
                    checking,
                    categoryId = groceries,
                    amountMinor = 7_777,
                    kind = TransactionKind.INCOME,
                    date = september12,
                ),
            )
            val holdingId = database.holdingDao().insert(holding(checking))
            val tradeId = database.tradeDao().insert(trade(holdingId))
            database.transactionDao().insert(
                transaction(checking, amountMinor = 40_000, date = september12, tradeId = tradeId),
            )

            val spent = budgets.observeSpending(september.first, september.second, "EUR").first()

            assertEquals(mapOf(groceries to Money(3_550, "EUR")), spent.byCategory)
            assertEquals(Money(3_550, "EUR"), spent.total)
        }

    @Test
    fun `spent in all counts uncategorized spending too, and still nothing that is not spending`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val september12 = LocalDate.of(2026, 9, 12)
            database.transactionDao().insert(
                transaction(checking, categoryId = groceries, amountMinor = 2_550, date = september12),
            )
            database.transactionDao().insert(transaction(checking, amountMinor = 1_000, date = september12))
            database.transactionDao().insert(
                transaction(
                    checking,
                    amountMinor = 7_777,
                    kind = TransactionKind.INCOME,
                    date = september12,
                ),
            )
            // A transfer's outgoing leg is money moved, not spent.
            val outId =
                database.transactionDao().insert(
                    transaction(checking, amountMinor = 50_000, date = september12),
                )
            database.transactionDao().setTransferId(id = outId, transferId = outId, updatedAt = clock.instant())
            database.transactionDao().insert(
                transaction(
                    savings,
                    kind = TransactionKind.INCOME,
                    amountMinor = 50_000,
                    date = september12,
                    transferId = outId,
                ),
            )

            val spent = budgets.observeSpending(september.first, september.second, "EUR").first()

            assertEquals(Money(3_550, "EUR"), spent.total)
            assertEquals(mapOf(groceries to Money(2_550, "EUR")), spent.byCategory)
        }

    @Test
    fun `there is at most one general budget, and a second one replaces its limit`() =
        runTest {
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            budgets.saveBudget(Budget(Budget.NEW, groceries, Money(40_000, "EUR")))

            val first = budgets.saveBudget(Budget(Budget.NEW, null, Money(150_000, "EUR")))
            val second = budgets.saveBudget(Budget(Budget.NEW, null, Money(120_000, "EUR")))

            assertEquals(first, second)
            val general = budgets.observeBudgets().first().filter { it.isGeneral }
            assertEquals(listOf(Money(120_000, "EUR")), general.map { it.limit })
            assertEquals(2, budgets.observeBudgets().first().size)
        }

    @Test
    fun `deleting a budget leaves its category and its movements alone`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val movement =
                database.transactionDao().insert(
                    transaction(checking, categoryId = groceries, amountMinor = 500),
                )
            val id = budgets.saveBudget(Budget(Budget.NEW, groceries, Money(40_000, "EUR")))

            budgets.deleteBudget(id)

            assertTrue(budgets.observeBudgets().first().isEmpty())
            assertNull(budgets.findById(id))
            assertEquals(500L, database.transactionDao().findById(movement)?.amountMinor)
        }
}

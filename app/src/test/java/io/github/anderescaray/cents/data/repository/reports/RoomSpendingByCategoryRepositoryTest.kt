package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
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
import java.time.YearMonth

/** One test class per report repository, the same way there is one file per view. */
@RunWith(RobolectricTestRunner::class)
class RoomSpendingByCategoryRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var repository: RoomSpendingByCategoryRepository

    @Before
    fun setUp() {
        database = createTestDatabase()
        repository = RoomSpendingByCategoryRepository(database.transactionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `shares are a fraction of the period total and add up to one`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val a = database.categoryDao().insert(category(name = "Rent"))
            val b = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, a, amountMinor = 75_000),
                    transaction(accountId, b, amountMinor = 25_000),
                ),
            )

            val breakdown = repository.observeSpendingByCategory(scope(august)).first()

            assertEquals(Money(100_000, "EUR"), breakdown.total)
            assertEquals(listOf(0.75f, 0.25f), breakdown.slices.map { it.share })
            assertEquals(1f, breakdown.slices.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
        }

    @Test
    fun `a period with nothing in it is empty rather than a division by zero`() =
        runTest {
            database.accountDao().insert(account())

            val breakdown = repository.observeSpendingByCategory(scope(august)).first()

            assertTrue(breakdown.isEmpty)
            assertEquals(Money.zero("EUR"), breakdown.total)
        }

    @Test
    fun `the same shape answers where the money came from`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val salary = database.categoryDao().insert(category(name = "Salary"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, salary, kind = TransactionKind.INCOME, amountMinor = 200_000),
                    transaction(accountId, salary, amountMinor = 5_000),
                ),
            )

            val income = repository.observeSpendingByCategory(scope(august), TransactionKind.INCOME).first()

            assertEquals(Money(200_000, "EUR"), income.total)
        }

    @Test
    fun `only the chosen account's spending is counted`() =
        runTest {
            val current = database.accountDao().insert(account(name = "Current"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(current, groceries, amountMinor = 3_000),
                    transaction(savings, groceries, amountMinor = 90_000),
                ),
            )

            val everything = repository.observeSpendingByCategory(scope(august)).first()
            val justCurrent = repository.observeSpendingByCategory(scope(august, current)).first()

            assertEquals(Money(93_000, "EUR"), everything.total)
            assertEquals(Money(3_000, "EUR"), justCurrent.total)
        }

    @Test
    fun `a scope is a transaction filter, so a slice opens that account's movements`() {
        val accountId = 7L

        val filter = scope(august, accountId).asFilter()

        assertEquals(LocalDate.of(2026, 8, 1), filter.from)
        assertEquals(LocalDate.of(2026, 8, 31), filter.to)
        // Without this the list would answer with every account's movements while the chart
        // above it added up one account's. The period alone can no longer build a filter,
        // which is what stops that being written by accident.
        assertEquals(accountId, filter.accountId)
    }

    private val august = ReportPeriod.Month(YearMonth.of(2026, 8))

    /**
     * The scope these tests report on. `accountId` defaults to null — every account — so a
     * test only names one when narrowing is what it is about.
     */
    private fun scope(
        period: ReportPeriod,
        accountId: Long? = null,
    ) = ReportScope(period, "EUR", accountId)
}

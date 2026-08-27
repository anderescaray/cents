package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
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

@RunWith(RobolectricTestRunner::class)
class RoomReportsRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var repository: RoomReportsRepository

    @Before
    fun setUp() {
        database = createTestDatabase()
        repository = RoomReportsRepository(database.transactionDao())
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

            val breakdown = repository.observeSpendingByCategory(august, "EUR").first()

            assertEquals(Money(100_000, "EUR"), breakdown.total)
            assertEquals(listOf(0.75f, 0.25f), breakdown.slices.map { it.share })
            assertEquals(1f, breakdown.slices.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
        }

    @Test
    fun `a period with nothing in it is empty rather than a division by zero`() =
        runTest {
            database.accountDao().insert(account())

            val breakdown = repository.observeSpendingByCategory(august, "EUR").first()

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

            val income = repository.observeSpendingByCategory(august, "EUR", TransactionKind.INCOME).first()

            assertEquals(Money(200_000, "EUR"), income.total)
        }

    @Test
    fun `months with no movements come back as zeros, not as gaps`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 200_000, date = june(1)),
                    transaction(accountId, amountMinor = 45_000, date = june(2)),
                    transaction(accountId, amountMinor = 12_000, date = LocalDate.of(2026, 8, 4)),
                ),
            )

            val flow =
                repository
                    .observeMonthlyFlow(
                        ReportPeriod.Custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)),
                        "EUR",
                    ).first()

            assertEquals(
                listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8)),
                flow.map { it.month },
            )
            assertTrue(flow[1].isEmpty)
            assertEquals(Money(155_000, "EUR"), flow.first().summary.net)
            assertEquals(Money(-12_000, "EUR"), flow.last().summary.net)
        }

    @Test
    fun `a period inside one month is one month, and a partial month still counts`() =
        runTest {
            database.accountDao().insert(account())

            val singleDay =
                repository
                    .observeMonthlyFlow(
                        ReportPeriod.Custom(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17)),
                        "EUR",
                    ).first()
            val acrossTheBoundary =
                repository
                    .observeMonthlyFlow(
                        ReportPeriod.Custom(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 2)),
                        "EUR",
                    ).first()

            assertEquals(listOf(YearMonth.of(2026, 8)), singleDay.map { it.month })
            assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)), acrossTheBoundary.map { it.month })
        }

    @Test
    fun `a period is a transaction filter, so a slice can open the movements behind it`() {
        val filter = ReportPeriod.Month(YearMonth.of(2026, 8)).asFilter()

        assertEquals(LocalDate.of(2026, 8, 1), filter.from)
        assertEquals(LocalDate.of(2026, 8, 31), filter.to)
    }

    private val august = ReportPeriod.Month(YearMonth.of(2026, 8))

    private fun june(day: Int) = LocalDate.of(2026, 6, day)
}

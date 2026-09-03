package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
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

@RunWith(RobolectricTestRunner::class)
class RoomMonthlyFlowRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var repository: RoomMonthlyFlowRepository

    @Before
    fun setUp() {
        database = createTestDatabase()
        repository = RoomMonthlyFlowRepository(database.transactionDao())
    }

    @After
    fun tearDown() {
        database.close()
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
                        scope(ReportPeriod.Custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))),
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
                        scope(ReportPeriod.Custom(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17))),
                    ).first()
            val acrossTheBoundary =
                repository
                    .observeMonthlyFlow(
                        scope(ReportPeriod.Custom(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 2))),
                    ).first()

            assertEquals(listOf(YearMonth.of(2026, 8)), singleDay.map { it.month })
            assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)), acrossTheBoundary.map { it.month })
        }

    @Test
    fun `only the chosen account's months are counted`() =
        runTest {
            val current = database.accountDao().insert(account(name = "Current"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(current, amountMinor = 3_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(savings, amountMinor = 90_000, date = LocalDate.of(2026, 8, 5)),
                ),
            )

            val august = ReportPeriod.Custom(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
            val everything = repository.observeMonthlyFlow(scope(august)).first()
            val justCurrent = repository.observeMonthlyFlow(scope(august, current)).first()

            assertEquals(
                93_000L,
                everything
                    .single()
                    .summary.expenses.minorUnits,
            )
            assertEquals(
                3_000L,
                justCurrent
                    .single()
                    .summary.expenses.minorUnits,
            )
        }

    private fun june(day: Int) = LocalDate.of(2026, 6, day)

    /**
     * The scope these tests report on. `accountId` defaults to null — every account — so a
     * test only names one when narrowing is what it is about.
     */
    private fun scope(
        period: ReportPeriod,
        accountId: Long? = null,
    ) = ReportScope(period, "EUR", accountId)
}

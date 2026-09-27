package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.FlowBucketSize
import io.github.anderescaray.cents.domain.model.FlowShape
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

            val flow = repository.observeFlow(scope(AUGUST), FlowShape(count = 3)).first()

            assertEquals(
                listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8)).map(ReportPeriod::Month),
                flow.buckets.map { it.whole },
            )
            assertTrue(flow.buckets[1].isEmpty)
            assertEquals(
                Money(155_000, "EUR"),
                flow.buckets
                    .first()
                    .summary.net,
            )
            assertEquals(
                Money(-12_000, "EUR"),
                flow.buckets
                    .last()
                    .summary.net,
            )
        }

    @Test
    fun `a quarter adds up its three months`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, amountMinor = 1_000, date = LocalDate.of(2026, 4, 3)),
                    transaction(accountId, amountMinor = 2_000, date = LocalDate.of(2026, 5, 3)),
                    transaction(accountId, amountMinor = 4_000, date = LocalDate.of(2026, 6, 30)),
                    transaction(accountId, amountMinor = 8_000, date = LocalDate.of(2026, 7, 1)),
                ),
            )

            val flow = repository.observeFlow(scope(AUGUST), FlowShape(FlowBucketSize.QUARTER, count = 2)).first()

            assertEquals(
                Money(7_000, "EUR"),
                flow.buckets
                    .first()
                    .summary.expenses,
            )
            assertEquals(
                Money(8_000, "EUR"),
                flow.buckets
                    .last()
                    .summary.expenses,
            )
        }

    @Test
    fun `the last column stops where the period does`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, amountMinor = 1_000, date = LocalDate.of(2026, 8, 10)),
                    transaction(accountId, amountMinor = 9_000, date = LocalDate.of(2026, 8, 25)),
                ),
            )
            val untilThe20th = ReportPeriod.Custom(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20))

            val flow = repository.observeFlow(scope(untilThe20th), FlowShape(count = 1)).first()

            assertEquals(
                Money(1_000, "EUR"),
                flow.buckets
                    .single()
                    .summary.expenses,
            )
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

            val everything = repository.observeFlow(scope(AUGUST), FlowShape()).first()
            val justCurrent = repository.observeFlow(scope(AUGUST, current), FlowShape()).first()

            assertEquals(
                93_000L,
                everything.buckets
                    .last()
                    .summary.expenses.minorUnits,
            )
            assertEquals(
                3_000L,
                justCurrent.buckets
                    .last()
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

    private companion object {
        val AUGUST = ReportPeriod.Month(YearMonth.of(2026, 8))
    }
}

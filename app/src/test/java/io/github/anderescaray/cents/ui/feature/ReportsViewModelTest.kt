package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.reports.PeriodPreset
import io.github.anderescaray.cents.ui.feature.reports.ReportsUiState
import io.github.anderescaray.cents.ui.feature.reports.ReportsViewModel
import io.github.anderescaray.cents.ui.feature.reports.periodFor
import io.github.anderescaray.cents.ui.feature.reports.section.BalanceOverTimeSection
import io.github.anderescaray.cents.ui.feature.reports.section.MonthlyFlowSection
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingSection
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReportsViewModelTest {
    private lateinit var database: CentsDatabase
    private lateinit var viewModel: ReportsViewModel

    // A Tuesday in the middle of August, so "this week" and "this quarter" have to be
    // worked out rather than accidentally matching the month.
    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        viewModel =
            ReportsViewModel(
                reportSections(database),
                RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                TransactionFilterStore(),
                clock,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `the tab opens on this month and reads real spending`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, groceries, amountMinor = 3_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(accountId, groceries, amountMinor = 1_000, date = LocalDate.of(2026, 8, 5)),
                    // July: outside the period the tab opens on.
                    transaction(accountId, groceries, amountMinor = 99_000, date = LocalDate.of(2026, 7, 4)),
                ),
            )

            viewModel.uiState.test {
                val state = awaitStateThat { it.spending != null }

                assertEquals(PeriodPreset.MONTH, state.preset)
                assertEquals(ReportPeriod.Month(YearMonth.of(2026, 8)), state.period)
                assertEquals(Money(4_000, "EUR"), state.spending?.total)
                assertEquals(
                    "Groceries",
                    state.spending
                        ?.slices
                        ?.single()
                        ?.name,
                )
            }
        }

    @Test
    fun `every registered view arrives, in the order the tab lists them`() =
        runTest {
            database.accountDao().insert(account())

            viewModel.uiState.test {
                val state = awaitStateThat { it.sections.isNotEmpty() }

                // The screen renders whatever is here; nothing in the view model names a
                // chart. This is the assertion a fourth view updates, and the only one.
                assertEquals(listOf("spending", "flow", "balance"), state.sections.map { it.key })
            }
        }

    @Test
    fun `no accounts is a different screen from an empty period`() =
        runTest {
            viewModel.uiState.test {
                val state = awaitStateThat { !it.isLoading }

                assertTrue(state.hasNoAccounts)
                assertTrue(state.sections.isEmpty())
            }
        }

    @Test
    fun `changing the period re-asks the database rather than filtering in memory`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, groceries, amountMinor = 4_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(accountId, groceries, amountMinor = 50_000, date = LocalDate.of(2026, 3, 4)),
                ),
            )

            viewModel.uiState.test {
                awaitStateThat { it.spending?.total == Money(4_000, "EUR") }

                viewModel.selectPreset(PeriodPreset.YEAR)

                // March only appears once the question is asked of the whole year.
                val year = awaitStateThat { it.preset == PeriodPreset.YEAR && it.spending != null }
                assertEquals(Money(54_000, "EUR"), year.spending?.total)
                assertEquals(ReportPeriod.Year(2026), year.period)
            }
        }

    @Test
    fun `changing the period moves every view at once`() =
        runTest {
            val accountId = database.accountDao().insert(account(openingBalanceMinor = 10_000))
            database.transactionDao().insert(
                transaction(accountId, amountMinor = 4_000, date = LocalDate.of(2026, 3, 4)),
            )

            viewModel.uiState.test {
                // August: March is behind the period, so the line starts already reduced by
                // it and the donut shows nothing at all.
                val month = awaitStateThat { it.sections.isNotEmpty() }
                assertEquals(true, month.spending?.isEmpty)
                assertEquals(Money(6_000, "EUR"), month.balance?.opening)

                viewModel.selectPreset(PeriodPreset.YEAR)

                val year = awaitStateThat { it.preset == PeriodPreset.YEAR && it.balance?.isEmpty == false }
                // The same movement, now inside the period on both charts at once.
                assertEquals(Money(4_000, "EUR"), year.spending?.total)
                assertEquals(Money(10_000, "EUR"), year.balance?.opening)
                assertEquals(Money(6_000, "EUR"), year.balance?.closing)
            }
        }

    @Test
    fun `switching to custom keeps the range that was on screen`() =
        runTest {
            database.accountDao().insert(account())

            viewModel.uiState.test {
                awaitStateThat { it.preset == PeriodPreset.MONTH }

                viewModel.selectPreset(PeriodPreset.CUSTOM)

                // The dead end device check d5 describes is the opposite of this: a preset
                // chosen once, and custom dates then unreachable or reset to today.
                val custom = awaitStateThat { it.preset == PeriodPreset.CUSTOM }
                assertEquals(LocalDate.of(2026, 8, 1), custom.customFrom)
                assertEquals(LocalDate.of(2026, 8, 31), custom.customTo)
            }
        }

    @Test
    fun `a range entered backwards is straightened instead of showing an empty chart`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insert(
                transaction(accountId, amountMinor = 2_500, date = LocalDate.of(2026, 8, 10)),
            )

            viewModel.uiState.test {
                awaitStateThat { it.spending != null }

                viewModel.setCustomRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1))

                val state = awaitStateThat { it.preset == PeriodPreset.CUSTOM && it.spending != null }
                assertEquals(LocalDate.of(2026, 8, 1), state.customFrom)
                assertEquals(LocalDate.of(2026, 8, 31), state.customTo)
                assertEquals(Money(2_500, "EUR"), state.spending?.total)
                // Straightened before the balance line sees it, so it draws a month of days
                // rather than nothing at all.
                assertEquals(AUGUST_DAYS, state.balance?.points?.size)
            }
        }

    @Test
    fun `the monthly chart covers every month of the period`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insert(
                transaction(
                    accountId,
                    kind = TransactionKind.INCOME,
                    amountMinor = 200_000,
                    date = LocalDate.of(2026, 2, 3),
                ),
            )

            viewModel.uiState.test {
                awaitStateThat { !it.isLoading }
                viewModel.selectPreset(PeriodPreset.YEAR)

                val year = awaitStateThat { it.preset == PeriodPreset.YEAR && (it.monthlyFlow?.size ?: 0) > 1 }
                val months = requireNotNull(year.monthlyFlow)
                assertEquals(MONTHS_IN_YEAR, months.size)
                assertEquals(Money(200_000, "EUR"), months[1].summary.income)
                assertTrue(months.first().isEmpty)
            }
        }

    @Test
    fun `a preset is the period containing today, not the one ending on it`() {
        // Tuesday 18 August 2026.
        val tuesday = LocalDate.of(2026, 8, 18)

        val week = periodFor(PeriodPreset.WEEK, tuesday)
        val quarter = periodFor(PeriodPreset.QUARTER, tuesday)

        assertEquals(LocalDate.of(2026, 8, 17), week.from)
        assertEquals(LocalDate.of(2026, 8, 23), week.to)
        assertEquals(LocalDate.of(2026, 7, 1), quarter.from)
        assertEquals(LocalDate.of(2026, 9, 30), quarter.to)
    }

    private companion object {
        const val MONTHS_IN_YEAR = 12
        const val AUGUST_DAYS = 31
    }
}

private val ReportsUiState.spending get() = section<SpendingSection>()?.breakdown

private val ReportsUiState.monthlyFlow get() = section<MonthlyFlowSection>()?.months

private val ReportsUiState.balance get() = section<BalanceOverTimeSection>()?.balance

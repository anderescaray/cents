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
import io.github.anderescaray.cents.ui.feature.reports.section.CategoryTrendSection
import io.github.anderescaray.cents.ui.feature.reports.section.MonthlyFlowSection
import io.github.anderescaray.cents.ui.feature.reports.section.PeriodComparisonSection
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingByWeekdaySection
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReportsViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private lateinit var viewModel: ReportsViewModel

    // A Tuesday in the middle of August, so "this week" and "this quarter" have to be
    // worked out rather than accidentally matching the month.
    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    // Held rather than built inline, because a drill-down is only observable through it.
    private val filters = TransactionFilterStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        viewModel =
            viewModels.keep(
                ReportsViewModel(
                    reportSections(database, clock),
                    RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                    filters,
                    clock,
                ),
            )
    }

    @After
    fun tearDown() {
        try {
            viewModels.cancelAll()
            if (::database.isInitialized) database.close()
        } finally {
            Dispatchers.resetMain()
        }
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
    fun `the ring section carries both directions, each with its own kind`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val salary = database.categoryDao().insert(category(name = "Salary"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, groceries, amountMinor = 4_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(
                        accountId,
                        salary,
                        kind = TransactionKind.INCOME,
                        amountMinor = 200_000,
                        date = LocalDate.of(2026, 8, 1),
                    ),
                ),
            )

            viewModel.uiState.test {
                // Both sides are asked for at once, so the toggle on screen never waits on
                // SQLite and the two can never be about different periods.
                val state = awaitStateThat { it.earning?.isEmpty == false }

                assertEquals(Money(4_000, "EUR"), state.spending?.total)
                assertEquals(Money(200_000, "EUR"), state.earning?.total)
                assertEquals(TransactionKind.EXPENSE, state.spending?.kind)
                assertEquals(TransactionKind.INCOME, state.earning?.kind)
                // The kind is what a drill-down carries, so an income slice cannot open a
                // list of the expenses filed under the same category.
                assertEquals(
                    TransactionKind.INCOME,
                    state.earning?.let { it.filterFor(it.slices.first()) }?.kind,
                )
            }
        }

    @Test
    fun `choosing an account narrows every view at once`() =
        runTest {
            val current = database.accountDao().insert(account(name = "Current", openingBalanceMinor = 100_000))
            val savings = database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 900_000))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(current, groceries, amountMinor = 3_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(savings, groceries, amountMinor = 90_000, date = LocalDate.of(2026, 8, 5)),
                ),
            )

            viewModel.uiState.test {
                awaitStateThat { it.spending?.total == Money(93_000, "EUR") }

                viewModel.selectAccount(current)

                // All three views move together, or the tab shows one account's spending
                // beside every account's balance and reads as a contradiction.
                val narrowed = awaitStateThat { it.accountId == current && it.spending != null }
                assertEquals(Money(3_000, "EUR"), narrowed.spending?.total)
                assertEquals(
                    3_000L,
                    narrowed.monthlyFlow
                        ?.single()
                        ?.summary
                        ?.expenses
                        ?.minorUnits,
                )
                assertEquals(Money(100_000, "EUR"), narrowed.balance?.opening)
            }
        }

    @Test
    fun `a slice tapped while one account is chosen opens that account's movements`() =
        runTest {
            val current = database.accountDao().insert(account(name = "Current"))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insert(
                transaction(current, groceries, amountMinor = 3_000, date = LocalDate.of(2026, 8, 4)),
            )

            viewModel.uiState.test {
                viewModel.selectAccount(current)
                val state = awaitStateThat { it.accountId == current && it.spending?.isEmpty == false }
                val breakdown = requireNotNull(state.spending)

                viewModel.drillDown(breakdown.filterFor(breakdown.slices.first()))
            }

            // Without the account on the filter the list would answer with every account's
            // movements while the ring above it added up one account's.
            assertEquals(current, filters.filter.value.accountId)
        }

    @Test
    fun `changing the period keeps the account being looked at`() =
        runTest {
            val current = database.accountDao().insert(account(name = "Current"))
            database.accountDao().insert(account(name = "Savings"))

            viewModel.uiState.test {
                viewModel.selectAccount(current)
                awaitStateThat { it.accountId == current }

                viewModel.selectPreset(PeriodPreset.YEAR)

                // Asking about a different stretch of time is not asking about different
                // money, and rebuilding the selection from scratch used to lose the account.
                val state = awaitStateThat { it.preset == PeriodPreset.YEAR }
                assertEquals(current, state.accountId)
            }
        }

    @Test
    fun `an account deleted while the tab is open falls back to all of them`() =
        runTest {
            val current = database.accountDao().insert(account(name = "Current"))
            database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 900_000))

            viewModel.uiState.test {
                viewModel.selectAccount(current)
                awaitStateThat { it.accountId == current }

                database.accountDao().deleteById(current)

                // The alternative is a tab reporting on nothing, headed by the name of an
                // account that no longer exists.
                val state = awaitStateThat { it.accounts.none { account -> account.id == current } }
                assertNull(state.accountId)
                assertEquals(Money(900_000, "EUR"), state.balance?.opening)
            }
        }

    @Test
    fun `the picker only appears once there is more than one account`() =
        runTest {
            database.accountDao().insert(account(name = "Current"))

            viewModel.uiState.test {
                val alone = awaitStateThat { it.accounts.size == 1 }
                assertFalse(alone.canPickAccount)

                database.accountDao().insert(account(name = "Savings"))

                assertTrue(awaitStateThat { it.accounts.size == 2 }.canPickAccount)
            }
        }

    @Test
    fun `every registered view arrives, in the order the tab lists them`() =
        runTest {
            database.accountDao().insert(account())

            viewModel.uiState.test {
                val state = awaitStateThat { it.sections.isNotEmpty() }

                // The screen renders whatever is here; nothing in the view model names a
                // chart. This is the assertion a new view updates, and the only one.
                assertEquals(
                    listOf("spending", "flow", "balance", "weekday", "comparison", "trend"),
                    state.sections.map { it.key },
                )
            }
        }

    @Test
    fun `the weekday view groups the period's spending by day of the week`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    // Monday 17 and Saturday 22 August 2026.
                    transaction(accountId, amountMinor = 4_000, date = LocalDate.of(2026, 8, 17)),
                    transaction(accountId, amountMinor = 9_000, date = LocalDate.of(2026, 8, 22)),
                    // July: outside the month the tab opens on.
                    transaction(accountId, amountMinor = 99_000, date = LocalDate.of(2026, 7, 20)),
                ),
            )

            viewModel.uiState.test {
                val state = awaitStateThat { it.weekday?.isEmpty == false }

                assertEquals(Money(4_000, "EUR"), state.weekday?.on(DayOfWeek.MONDAY)?.total)
                assertEquals(Money(9_000, "EUR"), state.weekday?.on(DayOfWeek.SATURDAY)?.total)
                assertEquals(Money(13_000, "EUR"), state.weekday?.total)
                // A bar drills down by a weekday, which the list can now express and the
                // Filters form can show and clear (D10).
                assertEquals(
                    DayOfWeek.SATURDAY,
                    state.weekday?.filterFor(DayOfWeek.SATURDAY)?.weekday,
                )
            }
        }

    @Test
    fun `the comparison reads the period before the one selected`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, amountMinor = 40_000, date = LocalDate.of(2026, 7, 10)),
                    transaction(accountId, amountMinor = 25_000, date = LocalDate.of(2026, 8, 4)),
                ),
            )

            viewModel.uiState.test {
                // The tab opens on August, so the view has to go and find July without
                // anything on screen having asked for it.
                val state = awaitStateThat { it.comparison?.isEmpty == false }

                assertEquals(Money(25_000, "EUR"), state.comparison?.current?.expenses)
                assertEquals(Money(40_000, "EUR"), state.comparison?.before?.expenses)
                assertEquals(Money(-15_000, "EUR"), state.comparison?.expensesChange)
            }
        }

    @Test
    fun `the comparison section carries both previous and same period last year`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    // August 2025 (year ago)
                    transaction(accountId, amountMinor = 35_000, date = LocalDate.of(2025, 8, 4)),
                    // July 2026 (previous period)
                    transaction(accountId, amountMinor = 40_000, date = LocalDate.of(2026, 7, 10)),
                    // August 2026 (current)
                    transaction(accountId, amountMinor = 25_000, date = LocalDate.of(2026, 8, 4)),
                ),
            )

            viewModel.uiState.test {
                val state = awaitStateThat { it.comparisonSection?.samePeriodLastYear?.isEmpty == false }
                val section = requireNotNull(state.comparisonSection)

                // Previous period: July 2026 (40_000)
                assertEquals(Money(40_000, "EUR"), section.previous.before.expenses)
                // Same period last year: August 2025 (35_000)
                assertEquals(Money(35_000, "EUR"), section.samePeriodLastYear.before.expenses)
                assertEquals(Money(25_000, "EUR"), section.samePeriodLastYear.current.expenses)
                assertEquals(Money(-10_000, "EUR"), section.samePeriodLastYear.expensesChange)
            }
        }

    @Test
    fun `the trend follows one category across buckets the period decides`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val rent = database.categoryDao().insert(category(name = "Rent"))
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, rent, amountMinor = 800_000, date = LocalDate.of(2026, 8, 1)),
                    transaction(accountId, food, amountMinor = 5_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(accountId, food, amountMinor = 4_000, date = LocalDate.of(2026, 6, 4)),
                ),
            )

            viewModel.uiState.test {
                val state = awaitStateThat { it.trend?.selected != null }

                // The tab is on a month, so the buckets are months — twelve of them, ending on
                // the one selected, which is more than the period holds. It is the one view
                // that reads outside it, and CategoryTrend argues why.
                assertEquals("Rent", state.trend?.selected?.name)
                assertEquals(TREND_BUCKETS, state.trend?.buckets?.size)
            }
        }

    @Test
    fun `pointing the trend at another category keeps it there`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val rent = database.categoryDao().insert(category(name = "Rent"))
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, rent, amountMinor = 800_000, date = LocalDate.of(2026, 8, 1)),
                    transaction(accountId, food, amountMinor = 5_000, date = LocalDate.of(2026, 8, 4)),
                ),
            )

            viewModel.uiState.test {
                val opened = awaitStateThat { it.trend?.selected != null }
                requireNotNull(opened.section<CategoryTrendSection>()).onSelectCategory(food)

                // The choice lives in the section's own source, so it survives the period
                // changing underneath it — nothing about a different month says stop watching
                // this category.
                awaitStateThat { it.trend?.selected?.name == "Restaurants" }
                viewModel.selectPreset(PeriodPreset.QUARTER)

                val later = awaitStateThat { it.preset == PeriodPreset.QUARTER && it.trend != null }
                assertEquals("Restaurants", later.trend?.selected?.name)
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
        const val TREND_BUCKETS = 12
        const val MONTHS_IN_YEAR = 12
        const val AUGUST_DAYS = 31
    }
}

private val ReportsUiState.spending get() = section<SpendingSection>()?.expense

private val ReportsUiState.earning get() = section<SpendingSection>()?.income

private val ReportsUiState.monthlyFlow get() = section<MonthlyFlowSection>()?.months

private val ReportsUiState.balance get() = section<BalanceOverTimeSection>()?.balance

private val ReportsUiState.weekday get() = section<SpendingByWeekdaySection>()?.spending

private val ReportsUiState.comparison get() = section<PeriodComparisonSection>()?.comparison

private val ReportsUiState.comparisonSection get() = section<PeriodComparisonSection>()

private val ReportsUiState.trend get() = section<CategoryTrendSection>()?.trend

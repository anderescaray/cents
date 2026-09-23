package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.budget
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.recurringRule
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomBudgetRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomRecurringRuleRepository
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.usecase.BudgetStatus
import io.github.anderescaray.cents.ui.feature.budgets.BudgetsViewModel
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class BudgetsViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private val clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
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
    fun `a budget reads this month's spending and the subscriptions still to come`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))
            val home = database.categoryDao().insert(category(name = "Home"))
            database.categoryDao().insert(category(name = "Groceries"))
            database.budgetDao().insert(budget(home, amountMinor = 100_000))
            database.transactionDao().insert(
                transaction(checking, categoryId = home, amountMinor = 20_000, date = LocalDate.of(2026, 9, 10)),
            )
            // The rent falls on the 30th: known, and not yet charged.
            database.recurringRuleDao().insert(
                recurringRule(
                    checking,
                    name = "Rent",
                    categoryId = home,
                    amountMinor = 65_000,
                    startDate = LocalDate.of(2026, 9, 30),
                ),
            )

            viewModel().uiState.test {
                val state = awaitStateThat { !it.isLoading && it.rows.isNotEmpty() }
                val row = state.rows.single()
                assertEquals(Money(20_000, "EUR"), row.spent)
                assertEquals(Money(65_000, "EUR"), row.committed)
                assertEquals(BudgetStatus.NEAR, row.status)
                assertEquals(YearMonth.of(2026, 9), state.month)
                // Groceries has no budget yet, so one can still be added.
                assertTrue(state.canAdd)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `with every expense category budgeted there is nothing left to add`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val home = database.categoryDao().insert(category(name = "Home"))
            database.budgetDao().insert(budget(home))

            viewModel().uiState.test {
                assertFalse(awaitStateThat { !it.isLoading && it.rows.isNotEmpty() }.canAdd)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `no budgets says so, and says whether a category exists to put one on`() =
        runTest {
            viewModel().uiState.test {
                val state = awaitStateThat { !it.isLoading }
                assertTrue(state.isEmpty)
                assertFalse(state.hasExpenseCategories)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun viewModel() =
        viewModels.keep(
            BudgetsViewModel(
                budgets = RoomBudgetRepository(database.budgetDao(), database.transactionDao(), clock),
                categories = RoomCategoryRepository(database.categoryDao(), database, clock),
                rules =
                    RoomRecurringRuleRepository(
                        database.recurringRuleDao(),
                        database.ruleReminderDao(),
                        database,
                        clock,
                    ),
                accounts = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                today = Today(clock),
            ),
        )
}

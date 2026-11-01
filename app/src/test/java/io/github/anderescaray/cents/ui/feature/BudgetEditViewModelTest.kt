package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.budget
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomBudgetRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.feature.budgets.BudgetEditViewModel
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BudgetEditViewModelTest {
    private lateinit var database: CentsDatabase
    private lateinit var budgets: RoomBudgetRepository
    private val viewModels = ViewModelKeeper()
    private val clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        budgets = RoomBudgetRepository(database.budgetDao(), database.transactionDao(), clock)
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
    fun `a new budget is offered only the expense categories without one`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val home = database.categoryDao().insert(category(name = "Home"))
            database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))
            database.budgetDao().insert(budget(groceries))
            database.budgetDao().insert(budget(null))

            val state = editorFor().uiState.first { it.loaded }

            assertEquals(listOf(home), state.choices.map { it.id })
            assertFalse("there is a general budget already", state.generalOffered)
            // The only choice is chosen already, rather than making the owner tap the obvious.
            assertEquals(home, state.categoryId)
        }

    @Test
    fun `a limit of nothing cannot be saved, and a real one can`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val editor = editorFor()
            editor.uiState.first { it.loaded }
            editor.chooseCategory(groceries)

            assertFalse(editor.uiState.value.canSave)
            "400".forEach(editor::appendDigit)
            assertTrue(editor.uiState.value.canSave)

            editor.save()
            editor.uiState.first { it.finished }

            val saved = budgets.observeBudgets().first().single()
            assertEquals(groceries, saved.categoryId)
            assertEquals(Money(40_000, "EUR"), saved.limit)
        }

    @Test
    fun `an existing budget opens with its limit and saves a new one in place`() =
        runTest {
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val id = database.budgetDao().insert(budget(groceries, amountMinor = 40_000))
            val editor = editorFor(id)

            val loaded = editor.uiState.first { it.loaded }
            assertEquals(Money(40_000, "EUR"), loaded.limit)
            assertFalse(loaded.isNew)

            editor.deleteDigit()
            editor.save()
            editor.uiState.first { it.finished }

            assertEquals(Money(4_000, "EUR"), budgets.findById(id)?.limit)
        }

    @Test
    fun `deleting asks first, and only the confirmation deletes`() =
        runTest {
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val id = database.budgetDao().insert(budget(groceries))
            val editor = editorFor(id)
            editor.uiState.first { it.loaded }

            editor.requestDelete()
            assertTrue(editor.uiState.value.confirmingDelete)
            editor.cancelDelete()
            assertEquals(id, budgets.findById(id)?.id)

            editor.requestDelete()
            editor.confirmDelete()
            editor.uiState.first { it.finished }

            assertNull(budgets.findById(id))
        }

    @Test
    fun `all spending is offered first while there is no general budget, and saves as one`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val editor = editorFor()
            val loaded = editor.uiState.first { it.loaded }
            assertTrue(loaded.generalOffered)
            // Two things to limit, so nothing is chosen for the owner.
            assertFalse(loaded.general)
            assertNull(loaded.categoryId)

            editor.chooseCategory(groceries)
            editor.chooseGeneral()
            "1500".forEach(editor::appendDigit)
            editor.save()
            editor.uiState.first { it.finished }

            val saved = budgets.observeBudgets().first().single()
            assertTrue(saved.isGeneral)
            assertEquals(Money(150_000, "EUR"), saved.limit)
        }

    @Test
    fun `with no expense category, all spending is the choice already`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))

            val state = editorFor().uiState.first { it.loaded }

            assertTrue(state.general)
        }

    @Test
    fun `the general budget opens as itself, with no category on offer`() =
        runTest {
            database.categoryDao().insert(category(name = "Groceries"))
            val id = database.budgetDao().insert(budget(null, amountMinor = 150_000))

            val state = editorFor(id).uiState.first { it.loaded }

            assertTrue(state.general)
            assertTrue(state.generalOffered)
            assertTrue(state.choices.isEmpty())
            assertTrue(state.canSave)
        }

    private fun editorFor(budgetId: Long = Destination.BudgetEdit.NEW) =
        viewModels.keep(
            BudgetEditViewModel(
                budgets = budgets,
                categories = RoomCategoryRepository(database.categoryDao(), database, clock),
                accounts = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                savedStateHandle = SavedStateHandle(mapOf("budgetId" to budgetId)),
            ),
        )
}

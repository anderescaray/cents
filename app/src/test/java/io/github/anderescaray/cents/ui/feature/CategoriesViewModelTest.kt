package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.ui.feature.categories.CategoriesViewModel
import io.github.anderescaray.cents.ui.feature.categories.DeletionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CategoriesViewModelTest {
    private lateinit var database: CentsDatabase
    private lateinit var viewModel: CategoriesViewModel

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        viewModel =
            CategoriesViewModel(
                RoomCategoryRepository(database.categoryDao(), database, clock),
            )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `a fresh install shows an empty list rather than a suggested one`() =
        runTest {
            viewModel.uiState.test {
                val state = awaitStateThat { !it.isLoading }
                assertEquals(emptyList<String>(), state.active.map { category -> category.name })
                assertEquals(true, state.showEmptyState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `each kind lists only its own categories`() =
        runTest {
            database.categoryDao().insert(category(name = "Rent", kind = CategoryKind.EXPENSE))
            database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))

            viewModel.uiState.test {
                val expenses = awaitStateThat { it.active.isNotEmpty() }
                assertEquals(listOf("Rent"), expenses.active.map { it.name })

                viewModel.setKind(CategoryKind.INCOME)

                val income = awaitStateThat { it.kind == CategoryKind.INCOME && it.active.isNotEmpty() }
                assertEquals(listOf("Salary"), income.active.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `archived categories are listed apart from the active ones`() =
        runTest {
            database.categoryDao().insert(category(name = "Rent"))
            database.categoryDao().insert(category(name = "Old habit", archived = true))

            viewModel.uiState.test {
                val state = awaitStateThat { it.active.isNotEmpty() && it.archived.isNotEmpty() }
                assertEquals(listOf("Rent"), state.active.map { it.name })
                assertEquals(listOf("Old habit"), state.archived.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dragging a category to the top rewrites the stored order`() =
        runTest {
            database.categoryDao().insert(category(name = "First", sortOrder = 0))
            database.categoryDao().insert(category(name = "Second", sortOrder = 1))
            database.categoryDao().insert(category(name = "Third", sortOrder = 2))

            viewModel.uiState.test {
                awaitStateThat { it.active.size == 3 }

                viewModel.moveCategory(from = 2, to = 0)

                val state = awaitStateThat { it.active.firstOrNull()?.name == "Third" }
                assertEquals(listOf("Third", "First", "Second"), state.active.map { it.name })
                assertEquals(listOf(0, 1, 2), state.active.map { it.sortOrder })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `swiping a category nobody uses reaches the same question, and deletes it`() =
        runTest {
            val id = database.categoryDao().insert(category(name = "Typo"))

            viewModel.uiState.test {
                awaitStateThat { it.active.isNotEmpty() }

                viewModel.deletion.request(id, "Typo")
                // Swiping asks; it does not delete. The alert names what it is about to do.
                val asked = viewModel.deletion.state.first { it.asking }
                assertEquals(DeletionKind.OUTRIGHT, asked.kind)
                assertEquals(
                    1,
                    database
                        .categoryDao()
                        .observeAll()
                        .first()
                        .size,
                )

                viewModel.deletion.confirm()
                awaitStateThat { it.active.isEmpty() }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `swiping a category that classifies movements archives it, exactly as the editor would`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val id = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insert(transaction(accountId = accountId, categoryId = id))

            viewModel.uiState.test {
                awaitStateThat { it.active.isNotEmpty() }

                viewModel.deletion.request(id, "Restaurants")
                val asked = viewModel.deletion.state.first { it.asking }
                // History is not rewritten by a gesture any more than by a button (rule 8).
                assertEquals(DeletionKind.ARCHIVE, asked.kind)
                assertEquals(1, asked.transactionCount)

                viewModel.deletion.confirm()
                val state = awaitStateThat { it.archived.isNotEmpty() }
                assertEquals(listOf("Restaurants"), state.archived.map { it.name })
                assertTrue(state.active.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, database.transactionDao().count())
        }

    @Test
    fun `cancelling the question leaves the category exactly where it was`() =
        runTest {
            val id = database.categoryDao().insert(category(name = "Rent"))

            viewModel.uiState.test {
                awaitStateThat { it.active.isNotEmpty() }

                viewModel.deletion.request(id, "Rent")
                viewModel.deletion.state.first { it.asking }
                viewModel.deletion.cancel()

                assertEquals(false, viewModel.deletion.state.value.asking)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("Rent"), categoriesInDatabase())
        }

    private suspend fun categoriesInDatabase(): List<String> =
        database
            .categoryDao()
            .observeAll()
            .first()
            .map { it.name }
}

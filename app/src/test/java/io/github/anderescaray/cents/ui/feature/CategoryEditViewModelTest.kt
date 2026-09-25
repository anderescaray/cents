package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.ui.feature.categories.CategoryEditViewModel
import io.github.anderescaray.cents.ui.feature.categories.DeletionKind
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
class CategoryEditViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private lateinit var categories: RoomCategoryRepository

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        categories = RoomCategoryRepository(database.categoryDao(), database, clock)
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

    private fun viewModelFor(
        categoryId: Long = Destination.CategoryEdit.NEW,
        kind: CategoryKind = CategoryKind.EXPENSE,
    ) = viewModels.keep(
        CategoryEditViewModel(
            categoryRepository = categories,
            savedStateHandle = SavedStateHandle(mapOf("categoryId" to categoryId, "kind" to kind)),
        ),
    )

    @Test
    fun `a new category is stored trimmed and at the end of its list`() =
        runTest {
            database.categoryDao().insert(category(name = "Rent", sortOrder = 0))
            val viewModel = viewModelFor()

            viewModel.uiState.test {
                viewModel.setName("  Groceries  ")
                viewModel.setColor(GREEN)
                viewModel.save()

                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            val stored = categories.observeCategories(CategoryKind.EXPENSE).first()
            assertEquals(listOf("Rent", "Groceries"), stored.map { it.name })
            assertEquals(1, stored.last().sortOrder)
            assertEquals(GREEN, stored.last().colorArgb)
        }

    @Test
    fun `a name another category of the same kind already uses is refused`() =
        runTest {
            database.categoryDao().insert(category(name = "Groceries"))
            val viewModel = viewModelFor()

            viewModel.uiState.test {
                viewModel.setName("groceries")

                val state = awaitStateThat { it.nameTaken }
                assertFalse(state.canSave)

                viewModel.save()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, categories.observeAllCategories().first().size)
        }

    @Test
    fun `the same name is free on the other kind`() =
        runTest {
            database.categoryDao().insert(category(name = "Gifts", kind = CategoryKind.EXPENSE))
            database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))
            val viewModel = viewModelFor(kind = CategoryKind.INCOME)

            viewModel.uiState.test {
                // Waiting for a name it does reject proves the existing names are loaded.
                viewModel.setName("Salary")
                awaitStateThat { it.nameTaken }

                viewModel.setName("Gifts")

                assertFalse(awaitStateThat { it.name == "Gifts" }.nameTaken)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `renaming an existing category keeps its identity`() =
        runTest {
            val id = database.categoryDao().insert(category(name = "Groceris"))
            val viewModel = viewModelFor(categoryId = id)

            viewModel.uiState.test {
                awaitStateThat { it.name == "Groceris" }

                viewModel.setName("Groceries")
                viewModel.save()

                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            val stored = categories.observeAllCategories().first().single()
            assertEquals(id, stored.id)
            assertEquals("Groceries", stored.name)
        }

    @Test
    fun `an existing category is checked against its own kind, not the route default`() =
        runTest {
            database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))
            database.categoryDao().insert(category(name = "Rent", kind = CategoryKind.EXPENSE))
            val id = database.categoryDao().insert(category(name = "Bonus", kind = CategoryKind.INCOME))
            // The list opens an existing category by id; the kind is whatever it turns out
            // to be once loaded, which is what the clash has to be measured against.
            val viewModel = viewModelFor(categoryId = id, kind = CategoryKind.EXPENSE)

            viewModel.uiState.test {
                awaitStateThat { it.kind == CategoryKind.INCOME }

                viewModel.setName("Salary")
                // The wait names the clash rather than the typing that leads to it. The
                // names to check against are rebuilt when the kind changes, in a collector
                // of its own, so a state carrying the new name can still be carrying the
                // previous kind's names — and on a slow machine, usually is.
                assertTrue(awaitStateThat { it.name == "Salary" && it.nameTaken }.nameTaken)

                // "Rent" is an expense category, so it says nothing about this income one.
                // Safe to wait on the name alone now: the assertion above proved the income
                // names have arrived, and setName recomputes the clash without suspending.
                viewModel.setName("Rent")
                assertFalse(awaitStateThat { it.name == "Rent" }.nameTaken)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tapping save twice stores one category, not two`() =
        runTest {
            val viewModel = viewModelFor()

            viewModel.uiState.test {
                viewModel.setName("Groceries")
                viewModel.save()
                viewModel.save()

                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("Groceries"), categories.observeAllCategories().first().map { it.name })
        }

    @Test
    fun `a restored category comes back at the end, not at the position it had`() =
        runTest {
            database.categoryDao().insert(category(name = "First", sortOrder = 0))
            database.categoryDao().insert(category(name = "Second", sortOrder = 1))
            // Archived while the active list was renumbered around it: its position is stale.
            val id = database.categoryDao().insert(category(name = "Back", archived = true, sortOrder = 0))
            val viewModel = viewModelFor(categoryId = id)

            viewModel.uiState.test {
                awaitStateThat { it.archived }
                viewModel.restore()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                listOf("First", "Second", "Back"),
                categories.observeCategories(CategoryKind.EXPENSE).first().map { it.name },
            )
        }

    @Test
    fun `a category nothing uses is deleted outright`() =
        runTest {
            val id = database.categoryDao().insert(category(name = "Typo"))
            val viewModel = viewModelFor(categoryId = id)

            viewModel.uiState.test {
                viewModel.requestDeletion()

                val state = viewModel.deletion.state.first { it.asking }
                assertEquals(DeletionKind.OUTRIGHT, state.kind)

                viewModel.deletion.confirm()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(emptyList<String>(), categories.observeAllCategories().first().map { it.name })
        }

    @Test
    fun `a category with transactions is archived instead, and its history is untouched`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val id = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insert(transaction(accountId = accountId, categoryId = id))
            val viewModel = viewModelFor(categoryId = id)

            viewModel.uiState.test {
                viewModel.requestDeletion()

                val state = viewModel.deletion.state.first { it.asking }
                assertEquals(DeletionKind.ARCHIVE, state.kind)
                assertEquals(1, state.transactionCount)

                viewModel.deletion.confirm()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(
                categories
                    .observeAllCategories()
                    .first()
                    .single()
                    .archived,
            )
            // The movement still exists and still carries the category that classified it.
            val movement =
                database
                    .transactionDao()
                    .observeFilteredWithNames(
                        accountId = null,
                        categoryId = null,
                        uncategorized = false,
                        kind = null,
                        groupId = null,
                        from = null,
                        to = null,
                        weekday = null,
                        minAmountMinor = null,
                        maxAmountMinor = null,
                        query = null,
                    ).first()
                    .single()
            assertEquals(id, movement.categoryId)
            assertEquals("Restaurants", movement.categoryName)
            // And it is no longer offered for new movements.
            assertEquals(
                emptyList<String>(),
                categories.observeCategories(CategoryKind.EXPENSE).first().map { it.name },
            )
        }

    @Test
    fun `restoring an archived category puts it back in the picker`() =
        runTest {
            val id = database.categoryDao().insert(category(name = "Leisure", archived = true))
            val viewModel = viewModelFor(categoryId = id)

            viewModel.uiState.test {
                awaitStateThat { it.archived }

                viewModel.restore()

                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                listOf("Leisure"),
                categories.observeCategories(CategoryKind.EXPENSE).first().map { it.name },
            )
        }

    private companion object {
        const val GREEN = 0xFF34C759.toInt()
    }
}

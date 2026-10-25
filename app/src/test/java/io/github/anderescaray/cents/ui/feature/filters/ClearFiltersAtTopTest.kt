package io.github.anderescaray.cents.ui.feature.filters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomGroupRepository
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Clearing every filter is the first thing on the Filters screen, so it is reached without
 * scrolling (backlog item 14), and it still clears the search and waits for Apply.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class ClearFiltersAtTopTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: CentsDatabase
    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)
    private val filters = TransactionFilterStore()

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `clearing is on screen without scrolling, and Apply empties filters and search`() {
        filters.setQuery("dinner")
        filters.apply(TransactionFilter.None.copy(kind = TransactionKind.EXPENSE))
        var done = 0
        composeRule.setContent {
            CentsTheme {
                TransactionFiltersScreen(
                    onDone = { done++ },
                    viewModel =
                        TransactionFiltersViewModel(
                            filters = filters,
                            accountRepository =
                                RoomAccountRepository(
                                    database.accountDao(),
                                    database.transactionDao(),
                                    database,
                                    clock,
                                ),
                            categoryRepository = RoomCategoryRepository(database.categoryDao(), database, clock),
                            groupRepository =
                                RoomGroupRepository(database.groupDao(), database.transactionDao(), clock),
                            clock = clock,
                        ),
                )
            }
        }

        composeRule.onNodeWithTag(CLEAR_ALL_FILTERS_TAG).assertIsDisplayed().performClick()
        assertEquals("nothing is applied before Apply", TransactionKind.EXPENSE, filters.filter.value.kind)

        composeRule.onNodeWithText("Apply").performClick()

        assertEquals(TransactionFilter.None, filters.filter.value)
        assertEquals(1, done)
    }
}

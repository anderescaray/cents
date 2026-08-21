package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.filters.DateBound
import io.github.anderescaray.cents.ui.feature.filters.DateRangePreset
import io.github.anderescaray.cents.ui.feature.filters.TransactionFiltersViewModel
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
import java.time.ZoneOffset

/** The filter form: a draft that only becomes the filter when it is applied. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionFiltersViewModelTest {
    private lateinit var database: CentsDatabase
    private lateinit var filters: TransactionFilterStore

    // A Wednesday in the middle of August, so "this month" and "last month" are unambiguous.
    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        filters = TransactionFilterStore()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() =
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
            clock = clock,
        )

    @Test
    fun `nothing changes until it is applied`() =
        runTest {
            val form = viewModel()

            form.setKind(TransactionKind.INCOME)
            form.setPreset(DateRangePreset.THIS_MONTH)

            // The list behind the form is still showing everything.
            assertEquals(TransactionFilter.None, filters.filter.value)

            form.apply()

            assertEquals(TransactionKind.INCOME, filters.filter.value.kind)
            assertEquals(LocalDate.of(2026, 8, 1), filters.filter.value.from)
            assertEquals(LocalDate.of(2026, 8, 31), filters.filter.value.to)
        }

    @Test
    fun `the presets write both ends of the range`() =
        runTest {
            val form = viewModel()

            form.uiState.test {
                form.setPreset(DateRangePreset.LAST_MONTH)
                val july = awaitStateThat { it.draft.from != null }
                assertEquals(LocalDate.of(2026, 7, 1), july.draft.from)
                assertEquals(LocalDate.of(2026, 7, 31), july.draft.to)
                assertEquals(DateRangePreset.LAST_MONTH, july.preset)

                form.setPreset(DateRangePreset.THIS_YEAR)
                val year = awaitStateThat { it.draft.from == LocalDate.of(2026, 1, 1) }
                assertEquals(LocalDate.of(2026, 12, 31), year.draft.to)

                form.setPreset(DateRangePreset.ANY)
                val any = awaitStateThat { it.draft.from == null }
                assertEquals(null, any.draft.to)
                assertEquals(DateRangePreset.ANY, any.preset)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a range typed by hand reads back as custom rather than as a preset`() =
        runTest {
            val form = viewModel()

            form.uiState.test {
                form.setPreset(DateRangePreset.CUSTOM)
                awaitStateThat { it.draft.from != null }

                form.openPicker(DateBound.FROM)
                form.setBound(LocalDate.of(2026, 3, 9))
                val custom = awaitStateThat { it.draft.from == LocalDate.of(2026, 3, 9) }

                assertEquals(DateRangePreset.CUSTOM, custom.preset)
                // The picker closes itself once it has been answered.
                assertEquals(null, custom.openPicker)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `amounts are typed as minor units, and blank is not zero`() =
        runTest {
            val form = viewModel()

            form.uiState.test {
                form.setMinAmount("2500")
                assertEquals(2_500L, awaitStateThat { it.draft.minAmountMinor != null }.draft.minAmountMinor)

                form.setMaxAmount("1000")
                val inverted = awaitStateThat { it.draft.maxAmountMinor != null }
                // 25,00 € to 10,00 € can never match, and the form has to say so rather than
                // showing an empty list that looks like an answer.
                assertTrue(inverted.draft.amountRangeInverted)

                form.setMinAmount("")
                val cleared = awaitStateThat { it.draft.minAmountMinor == null }
                assertEquals(false, cleared.draft.amountRangeInverted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the form offers archived accounts and categories, because history still points at them`() =
        runTest {
            database.accountDao().insert(account(name = "Old card", archived = true))
            database.categoryDao().insert(category(name = "Old habit", archived = true))
            val form = viewModel()

            form.uiState.test {
                val loaded = awaitStateThat { it.accounts.isNotEmpty() && it.categories.isNotEmpty() }
                assertEquals(listOf("Old card"), loaded.accounts.map { it.name })
                assertEquals(listOf("Old habit"), loaded.categories.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the form opens on whatever the list is already narrowed to`() =
        runTest {
            filters.apply(TransactionFilter(category = CategoryFilter.Uncategorized))

            val form = viewModel()

            assertEquals(CategoryFilter.Uncategorized, form.uiState.value.draft.category)
            assertEquals(false, form.uiState.value.isPristine)
        }

    @Test
    fun `clearing empties the form but still waits to be applied`() =
        runTest {
            filters.apply(TransactionFilter(kind = TransactionKind.EXPENSE))
            val form = viewModel()

            form.clear()

            assertEquals(TransactionKind.EXPENSE, filters.filter.value.kind)
            assertTrue(form.uiState.value.isPristine)

            form.apply()
            assertEquals(null, filters.filter.value.kind)
        }

    @Test
    fun `applying the form leaves the search text alone`() =
        runTest {
            filters.setQuery("restaurants")
            val form = viewModel()

            form.setKind(TransactionKind.EXPENSE)
            form.apply()

            // The search field lives on the list, not on this form: applying a filter must
            // not silently empty something the owner can see behind it.
            assertEquals("restaurants", filters.filter.value.query)
            assertEquals(TransactionKind.EXPENSE, filters.filter.value.kind)
        }

    @Test
    fun `show everything again means everything, search included`() =
        runTest {
            filters.setQuery("rent")
            filters.apply(TransactionFilter(kind = TransactionKind.EXPENSE))
            val form = viewModel()

            form.clear()
            form.apply()

            // The row promises everything. A list still narrowed to what was typed in a
            // field on another screen is not everything.
            assertEquals(TransactionFilter.None, filters.filter.value)
        }

    @Test
    fun `between two dates is reachable after a preset has been chosen`() =
        runTest {
            val form = viewModel()

            form.uiState.test {
                form.setPreset(DateRangePreset.THIS_MONTH)
                awaitStateThat { it.preset == DateRangePreset.THIS_MONTH }

                form.setPreset(DateRangePreset.CUSTOM)

                // The dates are already non-null from the preset, so a form that worked out
                // which preset they describe would answer THIS_MONTH here and never show the
                // two calendars at all.
                val custom = awaitStateThat { it.preset == DateRangePreset.CUSTOM }
                assertEquals(LocalDate.of(2026, 8, 1), custom.draft.from)
                assertEquals(LocalDate.of(2026, 8, 31), custom.draft.to)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a range that happens to match a preset still reads as custom once it was typed`() =
        runTest {
            val form = viewModel()

            form.uiState.test {
                form.setPreset(DateRangePreset.CUSTOM)
                awaitStateThat { it.preset == DateRangePreset.CUSTOM }

                form.openPicker(DateBound.TO)
                form.setBound(LocalDate.of(2026, 8, 31))

                // Aug 1 to Aug 31 is exactly "this month", and the form must not quietly
                // re-badge it as one: the owner asked for these two dates.
                assertEquals(
                    DateRangePreset.CUSTOM,
                    awaitStateThat { it.draft.to == LocalDate.of(2026, 8, 31) }.preset,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an absurdly long amount is capped rather than dropped`() =
        runTest {
            val form = viewModel()

            form.uiState.test {
                // Past Long.MAX_VALUE. Parsing this away to null would clear the bound and
                // blank the field while the owner was still typing into it.
                form.setMinAmount("9".repeat(30))

                assertEquals(999_999_999_999L, awaitStateThat { it.draft.minAmountMinor != null }.draft.minAmountMinor)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the amount range is said back in the account currency, not the locale`() =
        runTest {
            database.accountDao().insert(account(name = "Old dollars", currency = "USD"))
            val form = viewModel()

            form.uiState.test {
                assertEquals("USD", awaitStateThat { it.accounts.isNotEmpty() }.currency)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

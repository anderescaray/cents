package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomRecurringRuleRepository
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.recurring.RuleDateField
import io.github.anderescaray.cents.ui.feature.recurring.RuleFormViewModel
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
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The form that defines something repeating.
 *
 * Most of it is the movement form again. What is tested here is the part that is not: the two
 * answers hiding behind one *remove* button, which is rule 8 as a piece of interface — a rule
 * with charges behind it is stopped, and only one that never produced anything is deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RuleFormViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private var accountId: Long = 0
    private var categoryId: Long = 0

    private val today = LocalDate.of(2026, 9, 17)
    private val clock = Clock.fixed(Instant.parse("2026-09-17T09:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            database = createTestDatabase()
            accountId = database.accountDao().insert(account(name = "Checking"))
            categoryId = database.categoryDao().insert(category(name = "Subscriptions"))
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
    fun `a new rule starts today, on the first account, and cannot be saved empty`() =
        runTest {
            val viewModel = form()

            viewModel.uiState.test {
                val state = awaitStateThat { it.accountId != null }

                assertEquals(today, state.startDate)
                assertEquals(accountId, state.accountId)
                assertFalse("no name and no amount is not a subscription", state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving writes the rule the owner described`() =
        runTest {
            val viewModel = form()
            viewModel.setName("Netflix")
            viewModel.type("12.99")
            viewModel.selectCategory(categoryId)
            viewModel.setFrequency(Frequency.MONTHLY)

            save(viewModel)

            val saved = repository().observeRules().first().single()
            assertEquals("Netflix", saved.name)
            assertEquals(Money(1_299, "EUR"), saved.amount)
            assertEquals(categoryId, saved.categoryId)
            assertEquals(Frequency.MONTHLY, saved.frequency)
            assertTrue(saved.active)
        }

    @Test
    fun `saving a rule that began months ago writes the charges it missed, there and then`() =
        runTest {
            val viewModel = form()
            viewModel.setName("Rent")
            viewModel.type("750")
            viewModel.openDatePicker(RuleDateField.START)
            viewModel.setDate(LocalDate.of(2026, 5, 1))

            save(viewModel)

            // Found on the phone (`FEEDBACK.md`, 2026-09-17): a subscription entered in
            // September starting in May showed nothing at all, because generation only ran on
            // `ON_START` and the app had never left the foreground. The owner was looking at the
            // movement list a second later, which reads exactly like the feature not working.
            val written = database.transactionDao().observeAll().first()
            assertEquals(5, written.size)
            assertEquals(
                listOf(
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 9, 1),
                ),
                written.map { it.date }.sorted(),
            )
        }

    @Test
    fun `a rule that starts in the future writes nothing yet`() =
        runTest {
            val viewModel = form()
            viewModel.setName("Insurance")
            viewModel.type("300")
            viewModel.openDatePicker(RuleDateField.START)
            viewModel.setDate(LocalDate.of(2026, 12, 1))

            save(viewModel)

            // The other half of the same behaviour: catching up on save must not reach forward.
            assertTrue(
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `every so many days needs the number of days before it can be saved`() =
        runTest {
            val viewModel = form()
            viewModel.setName("Contact lenses")
            viewModel.type("20")
            viewModel.setFrequency(Frequency.EVERY_N_DAYS)

            viewModel.uiState.test {
                // A rule with no interval cannot come round at all, so it is not a rule.
                assertFalse(awaitStateThat { it.needsInterval }.canSave)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.setInterval("90")
            save(viewModel)

            assertEquals(
                90,
                repository()
                    .observeRules()
                    .first()
                    .single()
                    .intervalDays,
            )
        }

    @Test
    fun `an end date before the start date is refused`() =
        runTest {
            val viewModel = form()
            viewModel.setName("Gym")
            viewModel.type("30")
            viewModel.openDatePicker(RuleDateField.END)
            viewModel.setDate(today.minusMonths(1))

            viewModel.uiState.test {
                assertFalse(awaitStateThat { it.endDate != null }.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editing a rule keeps how far it has already got`() =
        runTest {
            val id = existingRule(start = LocalDate.of(2026, 6, 1))
            repository().generateDue()
            val generated = repository().findById(id)?.lastGenerated

            val viewModel = form(ruleId = id)
            viewModel.uiState.test {
                awaitStateThat { it.name == "Netflix" }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.setName("Netflix HD")
            save(viewModel)

            // Changing the price must not replay the year at the new one. The watermark is
            // the rule's memory of what it has written, and editing is not generating.
            assertEquals(generated, repository().findById(id)?.lastGenerated)
            assertEquals(
                4,
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun `a rule with charges behind it is stopped rather than deleted`() =
        runTest {
            val id = existingRule(start = LocalDate.of(2026, 6, 1))
            repository().generateDue()

            val viewModel = form(ruleId = id)
            viewModel.uiState.test {
                assertTrue(awaitStateThat { it.generatedCount > 0 }.hasHistory)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.requestRemoval()
            viewModel.confirmRemoval()

            viewModel.uiState.test {
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            // Rule 8: the rule is still there, no longer generating, and every movement it
            // made is exactly where it was. Deleting it would cut four real charges loose.
            val rule = repository().findById(id)
            assertFalse(requireNotNull(rule).active)
            assertEquals(
                4,
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun `a stopped rule can then be deleted, and its movements stay`() =
        runTest {
            val id = existingRule(start = LocalDate.of(2026, 6, 1))
            repository().generateDue()
            repository().setActive(id, active = false)

            val viewModel = form(ruleId = id)
            viewModel.uiState.test {
                val state = awaitStateThat { it.generatedCount > 0 }

                // The way out of "this account cannot be deleted while a subscription records
                // into it": stopping first, deleting second. Rule 8 survives it because the
                // movements are detached rather than removed.
                assertFalse(state.stopsRatherThanDeletes)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.requestRemoval()
            viewModel.confirmRemoval()

            viewModel.uiState.test {
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertNull(repository().findById(id))
            val written = database.transactionDao().observeAll().first()
            assertEquals(4, written.size)
            assertTrue("the charges are still there, orphaned", written.all { it.ruleId == null })
        }

    @Test
    fun `editing a stopped rule does not start it again`() =
        runTest {
            val id = existingRule(start = LocalDate.of(2026, 6, 1))
            repository().setActive(id, active = false)

            val viewModel = form(ruleId = id)
            viewModel.uiState.test {
                awaitStateThat { it.isEditing && !it.ruleActive }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.setName("Netflix Basic")
            save(viewModel)

            // Correcting the price of a cancelled subscription is not a request to resubscribe.
            assertFalse(requireNotNull(repository().findById(id)).active)
        }

    @Test
    fun `a rule that never produced anything is deleted outright`() =
        runTest {
            val id = existingRule(start = LocalDate.of(2027, 1, 1))

            val viewModel = form(ruleId = id)
            viewModel.uiState.test {
                assertFalse(awaitStateThat { it.isEditing }.hasHistory)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.requestRemoval()
            viewModel.confirmRemoval()

            viewModel.uiState.test {
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            // Nothing depends on it, so keeping a stopped row nobody wants would be clutter
            // dressed up as safety.
            assertNull(repository().findById(id))
        }

    @Test
    fun `switching to income offers income categories instead`() =
        runTest {
            val salaryCategory =
                database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))

            val viewModel = form()
            viewModel.selectCategory(categoryId)
            viewModel.setKind(TransactionKind.INCOME)

            viewModel.uiState.test {
                val state = awaitStateThat { it.kind == TransactionKind.INCOME && it.categories.isNotEmpty() }

                assertEquals(listOf(salaryCategory), state.categories.map { it.id })
                assertNull("the expense category it had no longer applies", state.categoryId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Types an amount the way the keypad does: digits, and the separator as its own key. */
    private fun RuleFormViewModel.type(amount: String) =
        amount.forEach { character ->
            if (character.isDigit()) appendDigit(character) else appendDecimalSeparator()
        }

    private suspend fun save(viewModel: RuleFormViewModel) {
        viewModel.uiState.test {
            awaitStateThat { it.canSave }
            viewModel.save()
            // The write lands on Room's executor, so reading the database on the next line
            // would race it. `finished` is set after it returns.
            awaitStateThat { it.finished }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun existingRule(start: LocalDate): Long =
        repository().createRule(
            RecurringRule(
                id = 0,
                name = "Netflix",
                accountId = accountId,
                categoryId = categoryId,
                kind = TransactionKind.EXPENSE,
                amount = Money(1_299, "EUR"),
                frequency = Frequency.MONTHLY,
                intervalDays = null,
                startDate = start,
                endDate = null,
                mode = RuleMode.AUTOMATIC,
                active = true,
                lastGenerated = null,
            ),
        )

    private fun form(ruleId: Long = Destination.RuleForm.NEW): RuleFormViewModel =
        viewModels.keep(
            RuleFormViewModel(
                repository(),
                RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                RoomCategoryRepository(database.categoryDao(), database, clock),
                SavedStateHandle(mapOf("ruleId" to ruleId)),
                clock,
            ),
        )

    private fun repository() = RoomRecurringRuleRepository(database.recurringRuleDao(), database, clock)
}

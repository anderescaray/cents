package io.github.anderescaray.cents.ui.feature

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomRecurringRuleRepository
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.ui.feature.recurring.CONFIRM_REMINDER_TAG
import io.github.anderescaray.cents.ui.feature.recurring.NewReminderSheet
import io.github.anderescaray.cents.ui.feature.recurring.REMINDER_DAYS_TAG
import io.github.anderescaray.cents.ui.feature.recurring.RuleFormViewModel
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.theme.CentsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The sheet a subscription's reminder is chosen in: suggestions as chips, a stepper that names the
 * reminder on one line and greys out at its limit, and one button that refuses a duplicate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RuleReminderSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private val clock = Clock.fixed(Instant.parse("2026-09-17T09:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        runBlocking { database.accountDao().insert(account(name = "Checking")) }
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
    fun `the stepper names the reminder, stops at on the day, and a taken one cannot be added`() {
        val form = showSheet()

        composeRule.onNodeWithTag(REMINDER_DAYS_TAG).assertTextEquals("The day before")
        composeRule.onNodeWithContentDescription("One day fewer").performClick()
        composeRule.onNodeWithTag(REMINDER_DAYS_TAG).assertTextEquals("On the day")
        composeRule.onNodeWithContentDescription("One day fewer").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("One day more").performClick()
        composeRule.onNodeWithContentDescription("One day more").performClick()
        composeRule.onNodeWithTag(REMINDER_DAYS_TAG).assertTextEquals("2 days before")
        composeRule.onNodeWithTag(CONFIRM_REMINDER_TAG).assertIsEnabled().performClick()

        assertEquals(
            listOf(-2),
            form.uiState.value.reminders
                .map { it.offsetDays },
        )
        assertFalse(form.uiState.value.addingReminder)

        form.startAddingReminder()
        form.chooseReminder(-2)
        composeRule.onNodeWithText("There is already a reminder for that day.").assertExists()
        composeRule.onNodeWithTag(CONFIRM_REMINDER_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a fixed amount is offered no after, a bill that asks is`() {
        val form = showSheet()
        composeRule.onNodeWithText("The day after").assertDoesNotExist()
        composeRule.onNodeWithText("After").assertDoesNotExist()

        form.setMode(RuleMode.CONFIRM)
        composeRule.onNodeWithText("The day after").assertExists()
        composeRule.onNodeWithText("After").assertExists()
    }

    private fun showSheet(): RuleFormViewModel {
        val form =
            viewModels.keep(
                RuleFormViewModel(
                    RoomRecurringRuleRepository(
                        database.recurringRuleDao(),
                        database.ruleReminderDao(),
                        database,
                        clock,
                    ),
                    RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                    RoomCategoryRepository(database.categoryDao(), database, clock),
                    SavedStateHandle(mapOf("ruleId" to Destination.RuleForm.NEW)),
                    clock,
                ),
            )
        form.startAddingReminder()
        composeRule.setContent {
            CentsTheme {
                val state by form.uiState.collectAsState()
                if (state.addingReminder) NewReminderSheet(state, form)
            }
        }
        composeRule.waitForIdle()
        return form
    }
}

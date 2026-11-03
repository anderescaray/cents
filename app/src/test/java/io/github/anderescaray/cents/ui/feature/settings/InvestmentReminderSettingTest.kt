package io.github.anderescaray.cents.ui.feature.settings

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings › Investments: the Friday reminder, on by default and turned off with one tap. */
@RunWith(RobolectricTestRunner::class)
class InvestmentReminderSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the Friday reminder is on, and a tap turns it off`() {
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            CentsTheme { SettingsContent(onOpenCategories = {}, onInvestmentReminderChanged = { changes += it }) }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(INVESTMENT_REMINDER_TAG))
        composeRule.onNodeWithTag(INVESTMENT_REMINDER_TAG).assertIsOn().performClick()

        assertEquals(listOf(false), changes)
    }
}

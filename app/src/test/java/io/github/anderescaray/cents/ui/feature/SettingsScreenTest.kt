package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.ui.feature.settings.SettingsScreen
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `resetting the bubbles asks first, and cancelling keeps the arrangement`() {
        var resets = 0
        setContent { resets++ }

        composeRule.onNodeWithText("Reset the bubble layout").performClick()

        // Rule 7: an arrangement made by hand can only be rebuilt by hand, and this row sits one
        // mis-tap away from everything else in Settings.
        composeRule.onNodeWithText("Reset the bubble layout?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(0, resets)
    }

    @Test
    fun `confirming resets it once`() {
        var resets = 0
        setContent { resets++ }

        composeRule.onNodeWithText("Reset the bubble layout").performClick()
        composeRule.onNodeWithText("Reset").performClick()

        assertEquals(1, resets)
        composeRule.onNodeWithText("Reset the bubble layout?").assertDoesNotExist()
    }

    private fun setContent(onResetHomeLayout: () -> Unit) {
        composeRule.setContent {
            CentsTheme {
                SettingsScreen(onOpenCategories = {}, onResetHomeLayout = onResetHomeLayout)
            }
        }
    }
}

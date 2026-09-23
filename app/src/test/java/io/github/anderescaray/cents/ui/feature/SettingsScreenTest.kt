package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.anderescaray.cents.ui.feature.settings.SettingsContent
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

        openReset()

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

        openReset()
        composeRule.onNodeWithText("Reset").performClick()

        assertEquals(1, resets)
        composeRule.onNodeWithText("Reset the bubble layout?").assertDoesNotExist()
    }

    /**
     * Scrolled to rather than assumed on screen: Settings is a lazy list, and a row further down
     * than the test's window is not composed at all until it is scrolled into view.
     */
    private fun openReset() {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(RESET_ROW))
        composeRule.onNodeWithText(RESET_ROW).performClick()
    }

    private fun setContent(onResetHomeLayout: () -> Unit) {
        composeRule.setContent {
            CentsTheme {
                SettingsContent(onOpenCategories = {}, onResetHomeLayout = onResetHomeLayout)
            }
        }
    }

    private companion object {
        const val RESET_ROW = "Reset the bubble layout"
    }
}

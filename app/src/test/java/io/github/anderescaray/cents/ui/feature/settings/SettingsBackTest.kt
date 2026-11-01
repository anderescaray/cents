package io.github.anderescaray.cents.ui.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings' back button names where it goes: the tab whose header opened it. */
@RunWith(RobolectricTestRunner::class)
class SettingsBackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `back is labelled Home by default`() {
        composeRule.setContent { CentsTheme { SettingsContent(onOpenCategories = {}) } }

        composeRule.onNodeWithContentDescription("Back to Home").assertIsDisplayed()
    }

    @Test
    fun `opened from Reports, back says Reports and goes there`() {
        var backs = 0
        composeRule.setContent {
            CentsTheme {
                SettingsContent(onOpenCategories = {}, onBack = { backs++ }, backLabel = R.string.tab_reports)
            }
        }

        composeRule.onNodeWithContentDescription("Back to Reports").performClick()

        assertEquals(1, backs)
    }
}

package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

/**
 * Settings after the redesign: one card of what organises the money, budgets no longer in it
 * (they moved to Reports), the bubble reset gone with the bubbles, and a way back to Home.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `each row of the first card opens its own screen`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            CentsTheme {
                SettingsContent(
                    onOpenCategories = { opened += "categories" },
                    onOpenSubscriptions = { opened += "subscriptions" },
                    onOpenGroups = { opened += "groups" },
                    onOpenCapture = { opened += "capture" },
                )
            }
        }

        listOf("Categories", "Subscriptions", "Groups").forEach { row ->
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(row))
            composeRule.onNodeWithText(row).performClick()
        }

        assertEquals(listOf("categories", "subscriptions", "groups"), opened)
    }

    @Test
    fun `budgets and the bubble reset are no longer settings`() {
        composeRule.setContent { CentsTheme { SettingsContent(onOpenCategories = {}) } }

        composeRule.onNodeWithText("Budgets").assertDoesNotExist()
        composeRule.onNodeWithText("Reset the bubble layout").assertDoesNotExist()
    }

    @Test
    fun `back returns home`() {
        var back = 0
        composeRule.setContent { CentsTheme { SettingsContent(onOpenCategories = {}, onBack = { back++ }) } }

        composeRule.onNodeWithContentDescription("Back to Home").performClick()

        assertEquals(1, back)
    }
}

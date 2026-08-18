package io.github.anderescaray.cents.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CentsListRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a row with an action is clickable and reports the click`() {
        var clicks = 0
        composeRule.setContent {
            CentsTheme {
                CentsListSection {
                    CentsListRow(title = "Groceries", value = "12,50 €", onClick = { clicks++ })
                }
            }
        }

        composeRule.onNodeWithText("Groceries").assertHasClickAction().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a row without an action is inert`() {
        composeRule.setContent {
            CentsTheme {
                CentsListSection {
                    CentsListRow(title = "No transactions yet")
                }
            }
        }

        composeRule.onNodeWithText("No transactions yet").assertHasNoClickAction()
    }

    @Test
    fun `title, subtitle and value are all shown`() {
        composeRule.setContent {
            CentsTheme {
                CentsListSection(header = "Today") {
                    CentsListRow(title = "Groceries", subtitle = "Checking", value = "12,50 €")
                }
            }
        }

        composeRule.onNodeWithText("Groceries").assertExists()
        composeRule.onNodeWithText("Checking").assertExists()
        composeRule.onNodeWithText("12,50 €").assertExists()
        // Section headers are uppercased by the component, as iOS does.
        composeRule.onNodeWithText("TODAY").assertExists()
    }
}

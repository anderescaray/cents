package io.github.anderescaray.cents.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CentsAlertTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the alert states what will happen and reports each answer separately`() {
        var confirmed = 0
        var dismissed = 0
        composeRule.setContent {
            CentsTheme {
                CentsAlert(
                    title = "Archive “Restaurants”?",
                    message = "3 movements use it, so it cannot be deleted.",
                    confirmLabel = "Archive",
                    onConfirm = { confirmed++ },
                    dismissLabel = "Cancel",
                    onDismiss = { dismissed++ },
                )
            }
        }

        composeRule.onNodeWithText("Archive “Restaurants”?").assertExists()
        composeRule.onNodeWithText("3 movements use it, so it cannot be deleted.").assertExists()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Archive").performClick()

        assertEquals(1, dismissed)
        assertEquals(1, confirmed)
    }

    @Test
    fun `a text field shows its placeholder until something is typed`() {
        var typed = ""
        composeRule.setContent {
            CentsTheme {
                CentsListSection {
                    CentsTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        placeholder = "Groceries",
                        modifier = Modifier.testTag("name"),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Groceries").assertExists()
        composeRule.onNodeWithTag("name").performTextInput("Rent")

        assertEquals("Rent", typed)
    }
}

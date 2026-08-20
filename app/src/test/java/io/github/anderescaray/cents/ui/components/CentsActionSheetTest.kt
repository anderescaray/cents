package io.github.anderescaray.cents.ui.components

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
class CentsActionSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `each choice reports itself, and cancel is separate from all of them`() {
        var reassigned = 0
        var deleted = 0
        var cancelled = 0
        composeRule.setContent {
            CentsTheme {
                CentsActionSheet(
                    title = "Delete “Old bank”?",
                    message = "It holds 3 movements.",
                    actions =
                        listOf(
                            CentsSheetAction(
                                label = "Move the movements to another account",
                                onClick = { reassigned++ },
                            ),
                            CentsSheetAction(
                                label = "Delete the account and its 3 movements",
                                onClick = { deleted++ },
                                destructive = true,
                            ),
                        ),
                    cancelLabel = "Cancel",
                    onDismiss = { cancelled++ },
                )
            }
        }

        composeRule.onNodeWithText("Delete “Old bank”?").assertExists()
        composeRule.onNodeWithText("Move the movements to another account").performClick()
        composeRule.onNodeWithText("Delete the account and its 3 movements").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, reassigned)
        assertEquals(1, deleted)
        assertEquals(1, cancelled)
    }

    @Test
    fun `a choice that cannot be taken stays on the sheet, inert, saying why`() {
        var taken = 0
        composeRule.setContent {
            CentsTheme {
                CentsActionSheet(
                    title = "Delete “Only account”?",
                    message = "It holds 1 movement.",
                    actions =
                        listOf(
                            CentsSheetAction(
                                label = "Move the movements to another account",
                                onClick = { taken++ },
                                enabled = false,
                                reason = "There is no other account in this currency to move them to.",
                            ),
                        ),
                    cancelLabel = "Cancel",
                    onDismiss = {},
                )
            }
        }

        // Visible and explained, rather than quietly missing: an option that disappears
        // for reasons the owner cannot see reads as the app having forgotten it.
        composeRule.onNodeWithText("Move the movements to another account").assertExists()
        composeRule
            .onNodeWithText("There is no other account in this currency to move them to.")
            .assertExists()

        composeRule.onNodeWithText("Move the movements to another account").performClick()

        assertEquals(0, taken)
    }
}

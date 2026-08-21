package io.github.anderescaray.cents.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CentsSwipeableRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var deletions = 0
    private var taps = 0

    private fun setContent() {
        composeRule.setContent {
            CentsTheme {
                CentsListSection {
                    CentsSwipeableRow(
                        onDelete = { deletions++ },
                        deleteLabel = "Delete",
                        modifier = Modifier.testTag(ROW_TAG),
                    ) { rowModifier ->
                        CentsListRow(
                            modifier = rowModifier,
                            title = "Groceries",
                            onClick = { taps++ },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `carrying the swipe across the row deletes without asking again`() {
        setContent()

        composeRule.onNodeWithTag(ROW_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(1, deletions)
        // The row tidies itself away: the answer is given to the dialog, not to a row
        // left hanging open behind it.
        assertEquals(0, taps)
    }

    @Test
    fun `a swipe that stops short does nothing at all`() {
        setContent()

        composeRule.onNodeWithTag(ROW_TAG).performTouchInput {
            swipeLeft(startX = right, endX = right - width * SHORT)
        }
        composeRule.waitForIdle()

        assertEquals(0, deletions)
        assertEquals(0, taps)
    }

    @Test
    fun `a partial swipe parks the row open, and the button it reveals deletes`() {
        setContent()

        composeRule.onNodeWithTag(ROW_TAG).performTouchInput {
            swipeLeft(startX = right, endX = right - width * PARTIAL)
        }
        composeRule.waitForIdle()
        assertEquals(0, deletions)

        composeRule.onNodeWithTag(DELETE_ACTION_TAG).performClick()

        assertEquals(1, deletions)
    }

    @Test
    fun `tapping an open row closes it instead of opening what it points at`() {
        setContent()

        composeRule.onNodeWithTag(ROW_TAG).performTouchInput {
            swipeLeft(startX = right, endX = right - width * PARTIAL)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Groceries").performClick()
        composeRule.waitForIdle()

        // Changing your mind about a swipe must not be the same gesture as opening the row.
        assertEquals(0, taps)
        assertEquals(0, deletions)

        // And once closed, a tap means what it always meant.
        composeRule.onNodeWithText("Groceries").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `the row still deletes for someone who cannot swipe`() {
        setContent()

        // TalkBack has no gesture to make, so the same action is announced on the row.
        val actions =
            composeRule
                .onNodeWithText("Groceries")
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]

        assertEquals("Delete", actions.single().label)
        composeRule.runOnUiThread { actions.single().action?.invoke() }
        composeRule.waitForIdle()

        assertEquals(1, deletions)
    }

    private companion object {
        const val ROW_TAG = "swipeable"

        /** Short of half the revealed button, so the row springs back. */
        const val SHORT = 0.10f

        /** Past the button but well short of the full swipe, so the row parks open. */
        const val PARTIAL = 0.35f
    }
}

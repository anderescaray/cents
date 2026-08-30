package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

    private fun setContent(rows: Int = 1) {
        composeRule.setContent {
            CentsTheme {
                CentsListSection {
                    repeat(rows) { index ->
                        SwipeableRow(index)
                    }
                }
            }
        }
    }

    @Composable
    private fun SwipeableRow(index: Int) {
        CentsSwipeableRow(
            onDelete = { deletions++ },
            deleteLabel = "Delete",
            modifier = Modifier.testTag(ROW_TAG + index),
        ) { rowModifier ->
            CentsListRow(
                modifier = rowModifier,
                title = rowTitle(index),
                onClick = { taps++ },
            )
        }
    }

    @Test
    fun `carrying the swipe across the row deletes without asking again`() {
        setContent()

        composeRule.onNodeWithTag(ROW_TAG + 0).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(1, deletions)
        // The row tidies itself away: the answer is given to the dialog, not to a row
        // left hanging open behind it.
        assertEquals(0, taps)
    }

    @Test
    fun `a swipe that stops short does nothing at all`() {
        setContent()

        composeRule.onNodeWithTag(ROW_TAG + 0).performTouchInput {
            swipeLeft(startX = right, endX = right - width * SHORT)
        }
        composeRule.waitForIdle()

        assertEquals(0, deletions)
        assertEquals(0, taps)
    }

    @Test
    fun `a swipe that stops in the middle springs back rather than staying open`() {
        setContent()

        // Far enough that the old component would have parked the row here with Delete
        // showing. The owner read that as the row getting stuck, so it no longer happens.
        composeRule.onNodeWithTag(ROW_TAG + 0).performTouchInput {
            swipeLeft(startX = right, endX = right - width * PARTIAL)
        }
        composeRule.waitForIdle()

        assertEquals(0, deletions)
        // Nothing is left open behind, so there is no button to find.
        composeRule.onAllNodesWithTag(DELETE_ACTION_TAG).assertCountEquals(0)
    }

    @Test
    fun `a tap after a swipe that came back means what a tap always meant`() {
        setContent()

        composeRule.onNodeWithTag(ROW_TAG + 0).performTouchInput {
            swipeLeft(startX = right, endX = right - width * PARTIAL)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(rowTitle(0)).performClick()

        // With no parked state there is nothing for a tap to dismiss, so it opens the row
        // rather than being swallowed — which is what the interceptor used to do.
        assertEquals(1, taps)
        assertEquals(0, deletions)
    }

    @Test
    fun `the row still deletes for someone who cannot swipe`() {
        setContent()

        // TalkBack has no gesture to make, so the same action is announced on the row.
        val actions =
            composeRule
                .onNodeWithText(rowTitle(0))
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]

        assertEquals("Delete", actions.single().label)
        composeRule.runOnUiThread { actions.single().action?.invoke() }
        composeRule.waitForIdle()

        assertEquals(1, deletions)
    }

    @Test
    fun `the strip a swipe reveals is never in the accessibility tree`() {
        setContent(rows = 3)

        // Composed for its colour, never published. Left reachable, TalkBack would read a
        // "Delete" button between every pair of rows, and D-pad focus would land on buttons
        // nobody can see — for exactly the users the custom action was added for.
        composeRule.onAllNodesWithTag(DELETE_ACTION_TAG).assertCountEquals(0)

        composeRule.onNodeWithTag(ROW_TAG + 1).performTouchInput {
            swipeLeft(startX = right, endX = right - width * PARTIAL)
        }
        composeRule.waitForIdle()

        // Still none: the strip is feedback during a drag, not a button that outlives it.
        composeRule.onAllNodesWithTag(DELETE_ACTION_TAG).assertCountEquals(0)
    }

    private fun rowTitle(index: Int) = "Row $index"

    private companion object {
        const val ROW_TAG = "swipeable"

        /** Barely a nudge. */
        const val SHORT = 0.10f

        /** The middle: far enough to reveal the strip, short of the swipe that fires. */
        const val PARTIAL = 0.35f
    }
}

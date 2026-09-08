package io.github.anderescaray.cents.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CentsTabBarShelfTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `collapsed, the bar is gone and the grabber offers it back`() {
        var collapsed: Boolean? = null
        composeRule.setContent {
            CentsTheme {
                CentsTabBarShelf(collapsed = true, onCollapsedChange = { collapsed = it }) {
                    Text("the tab bar")
                }
            }
        }

        composeRule.onNodeWithText("the tab bar").assertDoesNotExist()

        // A bar that can be hidden with no way back is a bar that is gone, so the grabber stays
        // and says what it will do.
        composeRule.onNodeWithContentDescription("Show the tab bar").performClick()
        assertEquals(false, collapsed)
    }

    @Test
    fun `showing, the grabber puts it away`() {
        var collapsed: Boolean? = null
        composeRule.setContent {
            CentsTheme {
                CentsTabBarShelf(collapsed = false, onCollapsedChange = { collapsed = it }) {
                    Text("the tab bar")
                }
            }
        }

        composeRule.onNodeWithText("the tab bar").assertExists()

        composeRule.onNodeWithContentDescription("Hide the tab bar").performClick()
        assertEquals(true, collapsed)
    }
}

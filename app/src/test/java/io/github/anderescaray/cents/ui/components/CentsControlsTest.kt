package io.github.anderescaray.cents.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CentsControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the switch reports the flipped value, not the current one`() {
        var received: Boolean? = null
        composeRule.setContent {
            CentsTheme {
                CentsSwitch(checked = false, onCheckedChange = { received = it })
            }
        }

        composeRule
            .onNode(
                androidx.compose.ui.test
                    .hasClickAction(),
            ).performClick()

        assertEquals(true, received)
    }

    @Test
    fun `a disabled switch is announced as disabled and swallows taps`() {
        var changed = false
        composeRule.setContent {
            CentsTheme {
                CentsSwitch(checked = false, onCheckedChange = { changed = true }, enabled = false)
            }
        }

        // The node still exists and still carries the switch role, so screen readers can
        // describe it; what it must not do is act on a tap.
        composeRule
            .onNode(
                androidx.compose.ui.test
                    .hasClickAction(),
            ).assertIsNotEnabled()
        composeRule
            .onNode(
                androidx.compose.ui.test
                    .hasClickAction(),
            ).performClick()

        assertTrue("A disabled switch must not report changes", !changed)
    }

    @Test
    fun `the button reports its taps`() {
        var taps = 0
        composeRule.setContent {
            CentsTheme {
                CentsButton(text = "Add transaction", onClick = { taps++ })
            }
        }

        composeRule.onNodeWithText("Add transaction").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `the tab bar reports the tab that was tapped`() {
        var selected: String? = null
        val items =
            listOf(
                CentsTabItem("TRANSACTIONS", "Transactions", R.drawable.ic_tab_transactions),
                CentsTabItem("REPORTS", "Reports", R.drawable.ic_tab_reports),
            )

        composeRule.setContent {
            CentsTheme {
                CentsTabBar(items = items, selectedKey = "TRANSACTIONS", onSelect = { selected = it })
            }
        }

        composeRule.onNodeWithText("Reports").performClick()

        assertEquals("REPORTS", selected)
    }
}

package io.github.anderescaray.cents.ui.feature.home

import android.provider.Settings
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.domain.model.BubblePlacement
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The canvas, with the device's animations turned off — which is both what a test needs (an
 * endless drift never goes idle, so nothing would ever settle) and a case worth asserting on:
 * with animations off a tap opens the section straight away rather than growing into it.
 */
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun stopAnimations() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun `every section is on the canvas, and each one opens`() {
        var opened: TopLevelTab? = null
        setContent(state = loaded(), onOpenSection = { opened = it })

        TopLevelTab.entries.forEach { tab ->
            composeRule.onNode(hasTestTag(bubbleTag(tab))).assertHasClickAction()
        }

        composeRule.onNodeWithTag(bubbleTag(TopLevelTab.REPORTS)).performClick()
        assertEquals(TopLevelTab.REPORTS, opened)
    }

    @Test
    fun `a bubble says which section it is, in words and not only as an icon`() {
        setContent(state = loaded())

        composeRule.onNodeWithText("Accounts").assertExists()
    }

    @Test
    fun `nothing is drawn until the layout has been read`() {
        var opened: TopLevelTab? = null
        setContent(state = HomeUiState(), onOpenSection = { opened = it })

        // Drawing the defaults first and correcting them a frame later is a canvas that jumps
        // on every launch.
        composeRule.onNodeWithTag(bubbleTag(TopLevelTab.ACCOUNTS)).assertDoesNotExist()
        assertNull(opened)
    }

    private fun setContent(
        state: HomeUiState,
        onMove: (TopLevelTab, BubblePlacement) -> Unit = { _, _ -> },
        onOpenSection: (TopLevelTab) -> Unit = {},
    ) {
        composeRule.setContent {
            CentsTheme {
                HomeScreen(state = state, onMove = onMove, onOpenSection = onOpenSection)
            }
        }
    }

    private fun loaded(): HomeUiState =
        HomeUiState(
            bubbles =
                TopLevelTab.entries.mapIndexed { index, tab ->
                    HomeBubble(tab = tab, placement = defaultPlacement(index))
                },
            loaded = true,
        )
}

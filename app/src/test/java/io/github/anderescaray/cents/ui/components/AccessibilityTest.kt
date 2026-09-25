package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What TalkBack, a thumb and a large system font get from the shared components (PRD Phase 6).
 *
 * Tested here, on the components, because every screen is built from them: a row that says it is
 * selected, a header that says it is a heading and a small button with a 48 dp target are true on
 * every screen that uses one.
 */
@RunWith(RobolectricTestRunner::class)
class AccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a choice says whether it is the chosen one, rather than reading a tick`() {
        composeRule.setContent {
            CentsTheme {
                CentsListSection {
                    CentsListRow(title = "Checking", selected = true, onClick = {})
                    CentsListRow(title = "Savings", selected = false, onClick = {})
                }
            }
        }

        composeRule
            .onNodeWithText("Checking")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithText("Savings").assertIsNotSelected()
        // The tick is drawn but not read out: "selected" already says it.
        composeRule.onNodeWithText("✓").assertDoesNotExist()
    }

    @Test
    fun `a row with an action is a button, and a row reads as one thing`() {
        composeRule.setContent {
            CentsTheme {
                CentsListSection {
                    CentsListRow(title = "Groceries", value = "12,50 €", onClick = {})
                }
            }
        }

        composeRule
            .onNodeWithText("Groceries")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            // Title and value are one node, so TalkBack reads them together.
            .assert(SemanticsMatcher("merged") { node -> node.config[SemanticsProperties.Text].size == 2 })
    }

    @Test
    fun `a section header is a heading`() {
        composeRule.setContent {
            CentsTheme {
                CentsListSection(header = "Today") {
                    CentsListRow(title = "Groceries")
                }
            }
        }

        composeRule.onNodeWithText("TODAY").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `the words in a modal bar are buttons a thumb can hit`() {
        composeRule.setContent {
            CentsTheme {
                CentsModalBar(
                    title = "Budget",
                    cancelLabel = "Cancel",
                    onCancel = {},
                    confirmLabel = "Save",
                    onConfirm = {},
                )
            }
        }

        composeRule
            .onNodeWithText("Save")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithText("Cancel").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Budget").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `a colour is said by its place in the grid, and the chosen one is selected`() {
        val colors = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        composeRule.setContent {
            CentsTheme {
                CentsColorPicker(colors = colors, selected = colors[1], onSelect = {})
            }
        }

        composeRule.onNodeWithContentDescription("Colour 1 of 3").assertIsNotSelected()
        composeRule
            .onNodeWithContentDescription("Colour 2 of 3")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `at a very large system font a control grows with its words`() {
        // A dialog opens its own window, which reads the font scale from the device rather than
        // from this CompositionLocal, so the alert cannot be measured this way; the segmented
        // control uses the same min-height pattern and lives in the window under test.
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 3f)) {
                CentsTheme {
                    CentsListSection(header = "Account") {
                        CentsListRow(title = "A long account name that has to wrap", value = "1.234,56 €")
                    }
                    CentsSegmentedControl(
                        options = listOf("Expense", "Income"),
                        selected = "Expense",
                        onSelect = {},
                        label = { it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("A long account name that has to wrap").assertIsDisplayed()
        // Three times a 20 sp line is 60 dp: a segment held at its old fixed 28 dp would cut the
        // word off, and one that grows is at least as tall as the line.
        composeRule.onNodeWithText("Expense").assertHeightIsAtLeast(50.dp)
    }
}

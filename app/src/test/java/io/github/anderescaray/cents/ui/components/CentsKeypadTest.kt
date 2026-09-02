package io.github.anderescaray.cents.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The keypad gained a surface and a hairline of its own, which put every key one layout
 * deeper than it used to be. Nothing about that should change which key reports what — so
 * these pin the wiring the restructuring could quietly have broken, rather than the look,
 * which is not something a test can judge.
 */
@RunWith(RobolectricTestRunner::class)
class CentsKeypadTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pressed = mutableListOf<String>()

    private fun setContent() {
        composeRule.setContent {
            CentsTheme {
                CentsKeypad(
                    onDigit = { pressed += it.toString() },
                    onDecimalSeparator = { pressed += "separator" },
                    onDelete = { pressed += "delete" },
                )
            }
        }
    }

    @Test
    fun `every digit reports itself`() {
        setContent()

        ('1'..'9').forEach { composeRule.onNodeWithText(it.toString()).performClick() }
        composeRule.onNodeWithText("0").performClick()

        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), pressed)
    }

    @Test
    fun `the separator key asks for cents rather than reporting a digit`() {
        setContent()

        composeRule.onNodeWithText(AmountEntry.separatorFor().toString()).performClick()

        assertEquals(listOf("separator"), pressed)
    }

    @Test
    fun `the delete key is reachable by its description, not by its glyph`() {
        setContent()

        // TalkBack has no way to read "⌫", which is why the key carries a description at all.
        composeRule.onNodeWithContentDescription("Delete").performClick()

        assertEquals(listOf("delete"), pressed)
    }
}

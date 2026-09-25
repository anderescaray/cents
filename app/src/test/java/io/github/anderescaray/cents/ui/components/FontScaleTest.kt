package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A control grows with its words at a large system font rather than cutting them off (PRD
 * Phase 6).
 *
 * Native graphics, because Robolectric's default graphics measure text with stand-in metrics that
 * do not follow the font size, so nothing would ever grow. A dialog opens its own window, which
 * reads the font scale from the device rather than from this CompositionLocal, so the alert is
 * not measured here; the segmented control uses the same min-height pattern.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `at a very large system font a control grows with its words`() {
        var fontScale by mutableFloatStateOf(1f)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides LinearFontScale(density.density, fontScale)) {
                CentsTheme {
                    CentsSegmentedControl(
                        options = listOf("Expense", "Income"),
                        selected = "Expense",
                        onSelect = {},
                        label = { it },
                    )
                }
            }
        }
        val normal = segmentHeight()

        fontScale = 3f
        val large = segmentHeight()

        assertTrue("segment is $normal at 1x and $large at 3x", large > normal * 1.5f)
    }

    private fun segmentHeight(): Dp =
        composeRule.onNodeWithText("Expense").getUnclippedBoundsInRoot().let { it.bottom - it.top }
}

/**
 * A font scale applied as a plain multiplier. Android 14 scales large text non-linearly, so the
 * test would otherwise depend on the platform's curve rather than on whether the control grows.
 */
private class LinearFontScale(
    override val density: Float,
    override val fontScale: Float,
) : Density {
    override fun TextUnit.toDp(): Dp = Dp(value * fontScale)

    override fun Dp.toSp(): TextUnit = (value / fontScale).sp
}

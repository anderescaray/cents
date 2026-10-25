package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Reports' period control in Spanish, at a phone's width: every label on one line, where
 * *Trimestre* used to wrap and be cut by the track.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentLabelFitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a long Spanish label stays on one line beside the short ones`() {
        composeRule.setContent {
            CentsTheme {
                CentsSegmentedControl(
                    options = listOf("Semana", "Mes", "Trimestre", "Año", "Fechas"),
                    selected = "Mes",
                    onSelect = {},
                    label = { it },
                    modifier = Modifier.width(PHONE_WIDTH.dp),
                )
            }
        }

        assertEquals("Trimestre is as tall as Mes, one line", height("Mes"), height("Trimestre"), 1f)
    }

    private fun height(label: String): Float =
        composeRule.onNodeWithText(label).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
}

/** A common phone's width, less Reports' two side margins. */
private const val PHONE_WIDTH = 360 - 32

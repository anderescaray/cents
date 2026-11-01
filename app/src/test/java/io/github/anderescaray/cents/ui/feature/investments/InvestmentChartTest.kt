package io.github.anderescaray.cents.ui.feature.investments

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.usecase.chartPoints
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Switching an investment chart between week, month and year, as the owner does on the phone,
 * with every history length that changes how many points each scale has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE) // Vico only measures its axes while drawing.
class InvestmentChartTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 1)

    private fun switchThroughEveryScale(first: LocalDate?) {
        var scale by mutableStateOf(ChartScale.WEEK)
        var view: View? = null
        composeRule.setContent {
            view = LocalView.current
            CentsTheme {
                InvestmentChart(
                    points =
                        chartPoints(
                            first,
                            today,
                            scale,
                        ) { day -> Money(100_000L + day.dayOfYear * 1_000L, "EUR") },
                    scale = scale,
                    onScaleChange = { scale = it },
                )
            }
        }
        composeRule.waitForIdle()
        // Frame by frame, as the phone runs: the crash came from the frame right after a tap,
        // drawn before the chart's new points had arrived.
        composeRule.mainClock.autoAdvance = false
        for (label in listOf("Weeks", "Months", "Years", "Months", "Weeks", "Years", "Weeks")) {
            composeRule.onNodeWithText(label).performClick()
            repeat(FRAMES_AFTER_TAP) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.runOnUiThread { draw(view!!) }
            }
            composeRule.mainClock.advanceTimeBy(ANIMATION_MS)
            composeRule.waitForIdle()
            composeRule.runOnUiThread { draw(view!!) }
        }
        composeRule.onNodeWithTag(CHART_SCALE_TAG).assertIsDisplayed()
    }

    @Test
    fun `no history switches scale without crashing`() = switchThroughEveryScale(null)

    @Test
    fun `one week of history switches scale without crashing`() = switchThroughEveryScale(today)

    @Test
    fun `two weeks in one month switch scale without crashing`() = switchThroughEveryScale(today.minusDays(7))

    @Test
    fun `a few months switch scale without crashing`() = switchThroughEveryScale(today.minusMonths(3))

    @Test
    fun `years of history switch scale without crashing`() = switchThroughEveryScale(today.minusYears(3))

    @Test
    fun `an axis label is never blank, whatever position Vico asks for`() {
        val months = listOf("Sep 26", "Oct 26", "Nov 26")
        assertEquals("Oct 26", axisLabel(months, 1.0))
        // Positions a week chart had, asked of a month chart's labels.
        assertEquals("Nov 26", axisLabel(months, 12.0))
        assertEquals("Sep 26", axisLabel(months, -1.0))
        assertTrue(axisLabel(emptyList(), 0.0).isNotBlank())
        assertTrue(axisLabel(null, 3.0).isNotBlank())
    }

    private fun draw(view: View) {
        val bitmap =
            Bitmap.createBitmap(
                view.width.coerceAtLeast(1),
                view.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        view.draw(Canvas(bitmap))
    }

    private companion object {
        const val ANIMATION_MS = 2_000L
        const val FRAMES_AFTER_TAP = 3
    }
}

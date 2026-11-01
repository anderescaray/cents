package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.usecase.chartPoints
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Switching an investment chart between week, month and year, as the owner does on the phone,
 * with every history length that changes how many points each scale has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class InvestmentChartTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 1)

    private fun switchThroughEveryScale(first: LocalDate?) {
        var scale by mutableStateOf(ChartScale.WEEK)
        composeRule.setContent {
            CentsTheme {
                InvestmentChart(
                    points = chartPoints(first, today, scale) { day -> Money(100_000L + day.dayOfYear * 1_000L, "EUR") },
                    scale = scale,
                    onScaleChange = { scale = it },
                )
            }
        }
        for (label in listOf("Weeks", "Months", "Years", "Months", "Weeks", "Years", "Weeks")) {
            composeRule.onNodeWithText(label).performClick()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(ANIMATION_MS)
            composeRule.waitForIdle()
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

    private companion object {
        const val ANIMATION_MS = 2_000L
    }
}

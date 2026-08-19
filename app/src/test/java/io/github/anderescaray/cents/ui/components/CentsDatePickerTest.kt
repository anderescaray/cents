package io.github.anderescaray.cents.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class CentsDatePickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val spain = Locale.forLanguageTag("es-ES")
    private val unitedStates = Locale.US

    @Test
    fun `the week starts where the locale says it does`() {
        assertEquals(DayOfWeek.MONDAY, weekdays(spain).first())
        assertEquals(DayOfWeek.SUNDAY, weekdays(unitedStates).first())
    }

    @Test
    fun `a month is padded so every day falls under its weekday`() {
        // 1 August 2026 is a Saturday: with weeks starting on Monday it needs five blanks.
        val cells = monthCells(YearMonth.of(2026, 8), spain)

        assertEquals(5, cells.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 8, 1), cells[5])
        assertEquals(31, cells.filterNotNull().size)
        assertEquals(0, cells.size % 7)
        assertNull(cells.last())
    }

    @Test
    fun `the same month starts a day earlier where the week starts on Sunday`() {
        assertEquals(6, monthCells(YearMonth.of(2026, 8), unitedStates).takeWhile { it == null }.size)
    }

    @Test
    fun `tapping a day reports that date, and the chevrons change month`() {
        var picked: LocalDate? = null
        composeRule.setContent {
            CentsTheme {
                CentsDatePicker(
                    selected = LocalDate.of(2026, 8, 18),
                    today = LocalDate.of(2026, 8, 18),
                    onSelect = { picked = it },
                    onDismiss = {},
                    locale = unitedStates,
                )
            }
        }

        composeRule.onNodeWithText("4").performClick()
        assertEquals(LocalDate.of(2026, 8, 4), picked)

        composeRule.onNodeWithContentDescription("Previous month").performClick()
        composeRule.onNodeWithText("31").performClick()
        assertEquals(LocalDate.of(2026, 7, 31), picked)
    }
}

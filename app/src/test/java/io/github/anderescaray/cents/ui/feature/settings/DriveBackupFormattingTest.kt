package io.github.anderescaray.cents.ui.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class DriveBackupFormattingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneOffset.UTC
    private val now =
        LocalDate
            .of(2026, 9, 22)
            .atTime(12, 0)
            .toInstant(zone)

    @Test
    @Config(qualifiers = "en")
    fun `no successful backup is never rather than the Unix epoch`() {
        assertEquals(listOf("Never"), labels(null))
    }

    @Test
    @Config(qualifiers = "en")
    fun `today and yesterday are the words the rest of the app uses, and older copies keep their date`() {
        val (today, yesterday, earlier) =
            labels(now.minusSeconds(60), now.minusSeconds(86_400), now.minusSeconds(3 * 86_400))

        assertTrue(today, today.startsWith("Today, "))
        assertTrue(yesterday, yesterday.startsWith("Yesterday, "))
        assertTrue(earlier, earlier.contains("19") && earlier.contains("2026"))
    }

    @Test
    @Config(qualifiers = "es")
    fun `relative times use the Spanish resources too`() {
        val (today, yesterday, never) = labels(now, now.minusSeconds(86_400), null)

        assertTrue(today, today.startsWith("Hoy, "))
        assertTrue(yesterday, yesterday.startsWith("Ayer, "))
        assertEquals("Nunca", never)
    }

    private fun labels(vararg times: Instant?): List<String> {
        val labels = mutableListOf<String>()
        composeRule.setContent {
            labels.clear()
            times.forEach { labels += relativeBackupTime(it, now, zone) }
        }
        composeRule.waitForIdle()
        return labels
    }
}

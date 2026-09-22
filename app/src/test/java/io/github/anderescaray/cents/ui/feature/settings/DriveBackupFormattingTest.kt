package io.github.anderescaray.cents.ui.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DriveBackupFormattingTest {
    private val now =
        LocalDate
            .of(2026, 9, 22)
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()

    @Test
    fun `no successful backup is never rather than the Unix epoch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(context.getString(R.string.drive_backup_never), relativeBackupTime(context, null, now))
    }

    @Test
    @Config(qualifiers = "en")
    fun `today and yesterday are relative but older copies keep their date`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(relativeBackupTime(context, now.minusSeconds(60), now).startsWith("Today at "))
        assertTrue(relativeBackupTime(context, now.minusSeconds(86_400), now).startsWith("Yesterday at "))
        val earlier = now.minusSeconds(3 * 86_400)
        assertEquals(backupDateTime(context, earlier), relativeBackupTime(context, earlier, now))
    }

    @Test
    @Config(qualifiers = "es")
    fun `relative times use the Spanish resources too`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(relativeBackupTime(context, now, now).startsWith("Hoy a las "))
        assertTrue(relativeBackupTime(context, now.minusSeconds(86_400), now).startsWith("Ayer a las "))
        assertEquals("Nunca", relativeBackupTime(context, null, now))
    }
}

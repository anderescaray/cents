package io.github.anderescaray.cents.ui.language

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app's language from Android 13, where the system keeps it (`AppLanguages`). Android 12 and
 * older, where the app keeps it itself, is `AppLanguagesBeforeAndroid13Test`.
 */
@RunWith(RobolectricTestRunner::class)
class AppLanguagesTest {
    private lateinit var context: Context
    private lateinit var system: LocaleManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        system = context.getSystemService(LocaleManager::class.java)
    }

    @Test
    fun `a new install follows the phone`() {
        assertTrue(AppLanguages.appliedBySystem)
        assertEquals(AppLanguage.SYSTEM, AppLanguages.current(context))
    }

    @Test
    fun `a choice is the system's per-app language, which the phone's own settings show too`() {
        AppLanguages.choose(context, AppLanguage.ENGLISH)

        assertEquals("en", system.applicationLocales.toLanguageTags())
        assertEquals(AppLanguage.ENGLISH, AppLanguages.current(context))

        AppLanguages.choose(context, AppLanguage.SYSTEM)

        assertTrue("following the phone again", system.applicationLocales.isEmpty)
        assertEquals(AppLanguage.SYSTEM, AppLanguages.current(context))
    }

    @Test
    fun `a regional choice from the phone's settings reads as its language`() {
        system.applicationLocales = LocaleList.forLanguageTags("es-MX")

        assertEquals(AppLanguage.SPANISH, AppLanguages.current(context))
    }

    @Test
    fun `the system applies it, so screens and notifications get their context as it is`() {
        AppLanguages.choose(context, AppLanguage.SPANISH)

        assertSame(context, AppLanguages.applyTo(context))
    }
}

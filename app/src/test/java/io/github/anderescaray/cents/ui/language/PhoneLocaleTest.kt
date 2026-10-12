package io.github.anderescaray.cents.ui.language

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.LocaleList
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.ui.util.deviceCurrency
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLocaleManager
import java.util.Locale

/**
 * The phone's locale on Android 13+, where a per-app language becomes part of the process's own
 * configuration and the system's resources take it on. Robolectric does not do that by itself,
 * so the test does: the configuration reads Spanish, as the app has been set to, while only the
 * system's list says what the phone is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es", shadows = [PhoneLocaleTest.PhoneLocales::class])
class PhoneLocaleTest {
    /** A [LocaleManager] whose system locales are the phone's, apart from the configuration. */
    @Implements(LocaleManager::class)
    class PhoneLocales : ShadowLocaleManager() {
        @Implementation
        public override fun getSystemLocales(): LocaleList {
            val answer = phone
            duringRead?.invoke()
            return answer
        }

        companion object {
            var phone: LocaleList = AMERICAN
            var duringRead: (() -> Unit)? = null
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val defaultBefore: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        PhoneLocales.phone = AMERICAN
        PhoneLocales.duringRead = null
        // As a process start would, so no reading from an earlier test is kept.
        PhoneLocale.onAppStart(context)
        Locale.setDefault(Locale.forLanguageTag("es"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultBefore)
    }

    @Test
    fun `the phone's locale is the system's, not the app's language`() {
        assertEquals(Locale.forLanguageTag("en-US"), PhoneLocale.get())
    }

    @Test
    fun `an American phone shown in Spanish keeps opening accounts in dollars`() {
        assertEquals("USD", deviceCurrency())
    }

    @Test
    fun `the phone's language is read again once the system says it changed`() {
        assertEquals(Locale.forLanguageTag("en-US"), PhoneLocale.get())

        // The app's configuration can stay as it was: only the broadcast says so.
        PhoneLocales.phone = SPANISH
        announceChange()

        assertEquals(Locale.forLanguageTag("es-ES"), PhoneLocale.get())
    }

    @Test
    fun `the phone's language is read again after the configuration changes`() {
        assertEquals(Locale.forLanguageTag("en-US"), PhoneLocale.get())

        PhoneLocales.phone = SPANISH
        PhoneLocale.onConfigurationChanged()

        assertEquals(Locale.forLanguageTag("es-ES"), PhoneLocale.get())
    }

    @Test
    fun `a read the change overtakes is not kept`() {
        // The phone changes while a worker is still asking for the old answer.
        PhoneLocales.duringRead = {
            PhoneLocales.duringRead = null
            PhoneLocales.phone = SPANISH
            announceChange()
        }

        assertEquals(Locale.forLanguageTag("en-US"), PhoneLocale.get())
        assertEquals(Locale.forLanguageTag("es-ES"), PhoneLocale.get())
    }

    private fun announceChange() {
        context.sendBroadcast(Intent(Intent.ACTION_LOCALE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        val AMERICAN: LocaleList = LocaleList.forLanguageTags("en-US")
        val SPANISH: LocaleList = LocaleList.forLanguageTags("es-ES")
    }
}

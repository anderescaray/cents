package io.github.anderescaray.cents.ui.language

import android.app.LocaleManager
import android.os.LocaleList
import io.github.anderescaray.cents.ui.util.deviceCurrency
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLocaleManager
import java.util.Locale

/**
 * The phone's locale on Android 13+, where a per-app language becomes part of the process's own
 * configuration and the system's resources take it on. Robolectric does not do that by itself,
 * so the test does: the configuration reads Spanish, as the app has been set to, while only the
 * system's list says the phone is American.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es", shadows = [PhoneLocaleTest.PhoneLocales::class])
class PhoneLocaleTest {
    /** A [LocaleManager] whose system locales are the phone's, apart from the configuration. */
    @Implements(LocaleManager::class)
    class PhoneLocales : ShadowLocaleManager() {
        @Implementation
        public override fun getSystemLocales(): LocaleList = LocaleList.forLanguageTags("en-US")
    }

    private val defaultBefore: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(defaultBefore)
    }

    @Test
    fun `the phone's locale is the system's, not the app's language`() {
        Locale.setDefault(Locale.forLanguageTag("es"))

        assertEquals(Locale.forLanguageTag("en-US"), AppLanguages.phoneLocale())
    }

    @Test
    fun `an American phone shown in Spanish keeps opening accounts in dollars`() {
        Locale.setDefault(Locale.forLanguageTag("es"))

        assertEquals("USD", deviceCurrency())
    }
}

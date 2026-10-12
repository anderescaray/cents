package io.github.anderescaray.cents.ui.language

import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.util.deviceCurrency
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Currency
import java.util.Locale

/**
 * The app's language on Android 12 and older, where there is no per-app language and the app
 * applies the choice itself: to the activity, to the notifiers, and to the default locale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S_V2])
class AppLanguagesBeforeAndroid13Test {
    private lateinit var context: Context
    private val defaultBefore: LocaleList = LocaleList.getDefault()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        // The default locales outlive the test: leave them as the next one expects.
        LocaleList.setDefault(defaultBefore)
    }

    @Test
    fun `the app keeps the choice itself, and reads it back`() {
        assertFalse(AppLanguages.appliedBySystem)
        assertEquals(AppLanguage.SYSTEM, AppLanguages.current(context))

        AppLanguages.choose(context, AppLanguage.SPANISH)

        assertEquals(AppLanguage.SPANISH, AppLanguages.current(context))
    }

    @Test
    fun `the activity and the notifiers are given a context in the chosen language`() {
        AppLanguages.choose(context, AppLanguage.SPANISH)

        val localized = AppLanguages.applyTo(context)

        assertEquals(
            "es",
            localized.resources.configuration.locales[0]
                .language,
        )
        assertEquals("Idioma", localized.getString(R.string.settings_language))
        assertEquals("Idioma del teléfono", localized.getString(R.string.settings_language_system))
    }

    @Test
    fun `amounts and dates follow it, as they do on Android 13`() {
        AppLanguages.choose(context, AppLanguage.SPANISH)
        assertEquals("es", Locale.getDefault().language)

        AppLanguages.choose(context, AppLanguage.SYSTEM)
        assertEquals(AppLanguages.phoneLocale(), Locale.getDefault())
    }

    @Test
    fun `following the phone leaves every context as it is`() {
        AppLanguages.choose(context, AppLanguage.SPANISH)
        AppLanguages.choose(context, AppLanguage.SYSTEM)

        assertSame(context, AppLanguages.applyTo(context))
    }

    @Test
    fun `the currency stays the phone's, whatever the language`() {
        AppLanguages.choose(context, AppLanguage.SPANISH)
        AppLanguages.applyTo(context)

        assertEquals(Currency.getInstance(AppLanguages.phoneLocale()).currencyCode, deviceCurrency())
    }

    @Test
    fun `the phone changing its own configuration does not take formats back to its language`() {
        AppLanguages.choose(context, AppLanguage.ENGLISH)
        // What the system does to the default locale on a change of its own configuration.
        Locale.setDefault(Locale.forLanguageTag("es-ES"))

        AppLanguages.onConfigurationChanged(context)

        assertEquals("en", Locale.getDefault().language)
    }

    @Test
    fun `a choice survives the process, being read from storage at start`() {
        AppLanguages.choose(context, AppLanguage.ENGLISH)
        Locale.setDefault(Locale.forLanguageTag("es-ES"))

        AppLanguages.onAppStart(context)

        assertEquals("en", Locale.getDefault().language)
    }
}

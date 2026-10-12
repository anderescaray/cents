package io.github.anderescaray.cents.ui.util

import android.app.LocaleManager
import android.os.LocaleList
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.util.medium
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLocaleManager
import java.time.LocalDate
import java.util.Locale

/**
 * `DECISIONS.md` D36: English chosen on a Spanish phone writes amounts and dates the phone's way.
 * As on Android 13+, the configuration and the default locale already read the app's English;
 * only the system's list still says the phone is Spanish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en", shadows = [FormatLocaleTest.SpanishPhone::class])
class FormatLocaleTest {
    @Implements(LocaleManager::class)
    class SpanishPhone : ShadowLocaleManager() {
        @Implementation
        public override fun getSystemLocales(): LocaleList = LocaleList.forLanguageTags("es-ES")
    }

    private val defaultBefore: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        // What the system does to the default locale once the app is set to English.
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultBefore)
    }

    @Test
    fun `formats follow the phone, not the app's language`() {
        assertEquals(Locale.forLanguageTag("es-ES"), formatLocale())
    }

    @Test
    fun `an amount is written the Spanish way in an English app`() {
        val written = MoneyFormatter.format(Money(1_234_567, "EUR"))

        assertTrue("was $written", written.startsWith("12.345,67") && written.endsWith("€"))
    }

    @Test
    fun `the keypad's decimal key stays the phone's comma`() {
        assertEquals(',', AmountEntry.separatorFor())
    }

    @Test
    fun `a date is written the Spanish way in an English app`() {
        // The medium date a group's span is written with.
        val written = LocalDate.of(2026, 10, 12).medium()

        assertTrue("was $written", written.startsWith("12 oct"))
    }
}

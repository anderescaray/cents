package io.github.anderescaray.cents.ui.util

import io.github.anderescaray.cents.ui.language.PhoneLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * A new account's currency is the phone's (D2), and the app's own language does not move it
 * (`docs/next-improvements.md`, item 1: "language must not change money"). Each test sets the
 * default locale the way the system does once the app has a language of its own.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceCurrencyTest {
    private val defaultBefore: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(defaultBefore)
    }

    @Test
    @Config(qualifiers = "es-rES")
    fun `a Spanish phone works in euros`() {
        assertEquals(Locale.forLanguageTag("es-ES"), PhoneLocale.get())
        assertEquals("EUR", deviceCurrency())
    }

    @Test
    @Config(qualifiers = "es-rES")
    fun `a Spanish phone shown in British English still works in euros, not pounds`() {
        Locale.setDefault(Locale.UK)

        assertEquals("EUR", deviceCurrency())
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `an American phone shown in Spanish still works in dollars`() {
        // A bare language, as the system sets it: no country, so no currency of its own.
        Locale.setDefault(Locale.forLanguageTag("es"))

        assertEquals("USD", deviceCurrency())
    }

    @Test
    fun `a locale with no country falls back rather than throwing`() {
        assertEquals("EUR", deviceCurrency(Locale.ENGLISH))
    }
}

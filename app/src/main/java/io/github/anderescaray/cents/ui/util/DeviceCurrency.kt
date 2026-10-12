package io.github.anderescaray.cents.ui.util

import java.util.Currency
import java.util.Locale

/**
 * The currency the app works in before any account says otherwise: the device's (D2).
 *
 * The one place a screen with no account to ask falls back to, so none of them guesses euros on
 * its own (rule 2). Euros only when the locale names no currency at all — a bare language with no
 * country — which a real phone does not have.
 *
 * **Read from the phone's region, never from the app's language.** Once the app is shown in a
 * language of its own, `Locale.getDefault()` is that language and nothing more, with no country to
 * name a currency: a phone in the United States shown in Spanish would otherwise open new accounts
 * in euros. Money does not change with the language.
 */
fun deviceCurrency(locale: Locale = formatLocale()): String =
    runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault(LAST_RESORT)

private const val LAST_RESORT = "EUR"

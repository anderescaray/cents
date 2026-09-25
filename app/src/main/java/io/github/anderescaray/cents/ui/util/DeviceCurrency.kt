package io.github.anderescaray.cents.ui.util

import java.util.Currency
import java.util.Locale

/**
 * The currency the app works in before any account says otherwise: the device's (D2).
 *
 * The one place a screen with no account to ask falls back to, so none of them guesses euros on
 * its own (rule 2). Euros only when the locale names no currency at all — a bare language with no
 * country — which a real phone does not have.
 */
fun deviceCurrency(locale: Locale = Locale.getDefault()): String =
    runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault(LAST_RESORT)

private const val LAST_RESORT = "EUR"

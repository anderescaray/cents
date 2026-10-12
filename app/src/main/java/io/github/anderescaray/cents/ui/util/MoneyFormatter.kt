package io.github.anderescaray.cents.ui.util

import io.github.anderescaray.cents.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Turns [Money] into text. This is the only place in the app where an amount stops being an
 * exact integer, and it happens at the very last moment, using the device locale — a Spanish
 * phone gets "1.234,56 €", an American one "$1,234.56".
 */
object MoneyFormatter {
    fun format(
        money: Money,
        locale: Locale = formatLocale(),
        withSign: Boolean = false,
    ): String {
        val currency = runCatching { Currency.getInstance(money.currency) }.getOrNull()
        val fractionDigits = currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
        // BigDecimal(unscaled, scale) keeps the exact value: no division through a Double.
        val amount = BigDecimal.valueOf(money.minorUnits, fractionDigits)

        val formatter =
            NumberFormat.getCurrencyInstance(locale).apply {
                if (currency != null) {
                    this.currency = currency
                }
                minimumFractionDigits = fractionDigits
                maximumFractionDigits = fractionDigits
            }

        val text = formatter.format(amount.abs())
        val sign =
            when {
                money.minorUnits < 0 -> MINUS
                withSign && money.minorUnits > 0 -> "+"
                else -> ""
            }
        return sign + text
    }

    /** Formats the amount the way it should appear in a list: expenses negative. */
    fun formatSigned(
        money: Money,
        locale: Locale = formatLocale(),
    ): String = format(money, locale, withSign = true)

    /** The typographic minus sign, which is what iOS uses in amounts. */
    private const val MINUS = "−"
}

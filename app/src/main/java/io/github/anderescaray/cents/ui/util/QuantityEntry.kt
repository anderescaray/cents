package io.github.anderescaray.cents.ui.util

import io.github.anderescaray.cents.domain.model.Quantity
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * A quantity of something held, while it is still being typed.
 *
 * The sibling of [AmountEntry], and deliberately the same rules through [DecimalEntry] — what
 * changes is only how many decimals are allowed. Money has two; a quantity has **eight**,
 * because 0.00000001 BTC is a real amount and a third of a share is a real amount, and refusing
 * either would make the field lie about what the owner holds.
 *
 * What is stored is a [Quantity], an exact integer of hundred-millionths. Nothing here produces
 * a `Double`.
 */
object QuantityEntry {
    /** The empty field: nothing typed, which is not the same as a quantity of zero. */
    const val EMPTY = ""

    /** Eight, matching [Quantity.SCALE]. */
    private const val FRACTION_DIGITS = 8

    /** Ten digits of units is past anything a personal portfolio holds. */
    private const val MAX_WHOLE_DIGITS = 10

    fun sanitize(typed: String): String = DecimalEntry.sanitize(typed, FRACTION_DIGITS, MAX_WHOLE_DIGITS)

    fun toQuantity(input: String): Quantity = Quantity(DecimalEntry.toScaled(input, FRACTION_DIGITS))

    fun fromQuantity(quantity: Quantity): String = DecimalEntry.fromScaled(quantity.scaled, FRACTION_DIGITS)

    /** Canonical text with the separator this phone writes, for showing inside a field. */
    fun toTypedText(
        input: String,
        locale: Locale = Locale.getDefault(),
    ): String = input.replace(DecimalEntry.SEPARATOR, AmountEntry.separatorFor(locale))

    /**
     * A quantity as it is read rather than typed: grouped, and with as many decimals as it
     * actually has — `12` stays `12`, `0,5` stays `0,5`, and a satoshi keeps all eight rather
     * than being rounded to nothing.
     */
    fun format(
        quantity: Quantity,
        locale: Locale = Locale.getDefault(),
    ): String {
        val value = BigDecimal.valueOf(quantity.scaled, FRACTION_DIGITS).stripTrailingZeros()
        val decimals = value.scale().coerceIn(0, FRACTION_DIGITS)
        return NumberFormat
            .getNumberInstance(locale)
            .apply {
                minimumFractionDigits = decimals
                maximumFractionDigits = decimals
            }.format(value)
    }
}

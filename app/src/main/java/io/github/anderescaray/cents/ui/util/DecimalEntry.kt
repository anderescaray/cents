package io.github.anderescaray.cents.ui.util

/**
 * The rules for a decimal number while it is still being typed, with the number of decimals
 * left to the caller.
 *
 * This is [AmountEntry]'s loop, lifted out when a second kind of number arrived: a quantity of
 * shares or crypto, which needs eight decimals rather than two ([QuantityEntry]). The rules
 * themselves are not negotiable and are argued for in [AmountEntry] — a digit past the limit is
 * refused rather than rounded away, a second separator does nothing, a leading separator reads
 * as `0.`, and delete removes exactly one character.
 *
 * Having them in one place is the point. Two copies of "what a half-typed number means" would
 * drift, and the direction they drift in is a number that is off by a factor of ten.
 */
internal object DecimalEntry {
    /** Locale-independent, because this is state and not display. */
    const val SEPARATOR = '.'

    /** Both separators are accepted from a keyboard, whatever the locale says. */
    private const val ACCEPTED_SEPARATORS = ".,"

    private const val ZERO = '0'

    fun sanitize(
        typed: String,
        fractionDigits: Int,
        maxWholeDigits: Int,
    ): String {
        val text = StringBuilder()
        var separated = false
        var fraction = 0
        for (character in typed) {
            when {
                character.isDigit() && separated -> {
                    if (fraction < fractionDigits) {
                        text.append(character)
                        fraction++
                    }
                }

                character.isDigit() -> {
                    // A lone leading zero is a placeholder, not a digit: 0 then 7 is 7.
                    if (text.length == 1 && text[0] == ZERO) {
                        text[0] = character
                    } else if (text.length < maxWholeDigits) {
                        text.append(character)
                    }
                }

                character in ACCEPTED_SEPARATORS && !separated -> {
                    separated = true
                    if (text.isEmpty()) text.append(ZERO)
                    text.append(SEPARATOR)
                }
            }
        }
        return text.toString()
    }

    /** The typed text as an exact integer of [fractionDigits] decimal places. */
    fun toScaled(
        input: String,
        fractionDigits: Int,
    ): Long {
        val separator = input.indexOf(SEPARATOR)
        val whole = if (separator < 0) input else input.take(separator)
        val fraction = if (separator < 0) "" else input.substring(separator + 1)
        return (whole + fraction.padEnd(fractionDigits, ZERO)).toLongOrNull() ?: 0L
    }

    /**
     * The text somebody would have typed to reach [scaled], with trailing zeros dropped: a
     * whole number comes back without a separator, so backspace peels off digits that are
     * really there rather than zeros nobody typed.
     */
    fun fromScaled(
        scaled: Long,
        fractionDigits: Int,
    ): String {
        if (scaled == 0L) return ""
        val magnitude = if (scaled < 0) -scaled else scaled
        val unit = POWERS_OF_TEN[fractionDigits]
        val whole = magnitude / unit
        val fraction = (magnitude % unit).toString().padStart(fractionDigits, ZERO).trimEnd(ZERO)
        return if (fraction.isEmpty()) whole.toString() else "$whole$SEPARATOR$fraction"
    }

    private val POWERS_OF_TEN =
        LongArray(19) { index ->
            var value = 1L
            repeat(index) { value *= 10 }
            value
        }
}

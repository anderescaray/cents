package io.github.anderescaray.cents.ui.util

import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * An amount while it is still being typed.
 *
 * Amounts are **typed in major units** — euros — with a decimal separator only when cents
 * are wanted: `2` `5` is 25 €, and `2` `,` `5` is 2,50 €. The keypad used to count in minor
 * units, which made every round number cost two extra taps on `0` (`FEEDBACK.md`, 2026-08-30).
 *
 * What is stored is unchanged and not negotiable: [toMinorUnits] is what a form hands to
 * [io.github.anderescaray.cents.domain.model.Money], and that is still an integer number of
 * cents (rule 1). This file changes how keystrokes are *read*, never how money is kept.
 *
 * The in-progress text is held in a **canonical** form: digits plus at most one [SEPARATOR],
 * which is always `'.'` whatever the device locale says. A locale-dependent string in state
 * would mean the value of a saved movement depended on the phone's language settings. The
 * locale appears only in [format] and [separatorFor], at the last moment, exactly as it does
 * in [MoneyFormatter].
 *
 * The rules, all of them enforced by [sanitize] so the keypad and the amount filter cannot
 * drift apart:
 *
 * - **At most two digits after the separator.** A third does not go in, rather than being
 *   accepted and rounded away — the owner's explicit answer, and the honest one: a digit
 *   that changes nothing must not appear to have been taken.
 * - **A second separator is refused.** `2,5,` is not a number, and the alternative — moving
 *   the separator to wherever it was typed last — silently multiplies the amount by a hundred.
 * - **A leading separator reads as `0,`.** Typing `,` `5` `0` on an empty field means fifty
 *   cents. Refusing it would leave a key that does nothing exactly where it is most obviously
 *   meant.
 * - **Delete removes one character, separator included.** `2,50` peels back to `2,5`, then
 *   `2,`, then `2`, then empty. One tap is one character everywhere, so nothing disappears in
 *   a tap that looked like a small correction.
 */
object AmountEntry {
    /** The empty field: no amount typed at all, which is not the same number as zero. */
    const val EMPTY = ""

    /** Locale-independent, because this is state and not display. */
    const val SEPARATOR = '.'

    /**
     * Both separators are accepted from a keyboard. A phone set to English offers a `.` and
     * one set to Spanish a `,`, and a hardware or third-party keyboard may offer either
     * whatever the locale says; refusing the "wrong" one would leave the owner tapping a key
     * that does nothing. The keypad has a single key, labelled by [separatorFor].
     */
    private const val ACCEPTED_SEPARATORS = ".,"

    /**
     * Two, because the app works in one currency — the device's — and it has cents (D2).
     * A currency with a different number of decimals is not something this app offers, and
     * pretending otherwise here would mean a keypad whose rules changed under the owner.
     */
    private const val FRACTION_DIGITS = 2

    /** Twelve digits of euros is past any figure this app will ever hold. */
    private const val MAX_WHOLE_DIGITS = 12

    private const val ZERO = '0'

    /** The typographic minus sign, which is what iOS uses in amounts. */
    private const val MINUS = "−"

    private const val HUNDRED = 100L

    /**
     * The only place canonical text is ever built. Everything else — a keypad tap, a
     * character typed into a text field, a whole string pasted into one — goes through here,
     * so the rules above hold no matter where the characters came from.
     *
     * Anything that is not a digit or a separator is dropped rather than rejecting the whole
     * string: pasting "25,00 €" should leave 25,00 behind, not an empty field.
     *
     * The **first** separator is the decimal one, so a pasted grouped number like "1.234,56"
     * reads as 1,23 rather than being unpicked. Working out which separator is grouping would
     * have to be done while typing too — nothing here can tell a paste from a keystroke — and
     * there it would turn a stray second comma into a hundredfold error. Predictable beats
     * clever when the stakes are what an amount is worth.
     */
    fun sanitize(typed: String): String {
        val text = StringBuilder()
        var separated = false
        var fraction = 0
        for (character in typed) {
            when {
                character.isDigit() && separated -> {
                    if (fraction < FRACTION_DIGITS) {
                        text.append(character)
                        fraction++
                    }
                }

                character.isDigit() -> {
                    // A lone leading zero is a placeholder, not a digit: 0 then 7 is 7.
                    if (text.length == 1 && text[0] == ZERO) {
                        text[0] = character
                    } else if (text.length < MAX_WHOLE_DIGITS) {
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

    /** One keystroke: a digit, or the separator key. */
    fun append(
        input: String,
        key: Char,
    ): String = sanitize(input + key)

    /** One character back, whatever that character was. */
    fun delete(input: String): String = input.dropLast(1)

    /** What a form actually saves: an exact integer number of cents (rule 1). */
    fun toMinorUnits(input: String): Long {
        val separator = input.indexOf(SEPARATOR)
        val whole = if (separator < 0) input else input.take(separator)
        val fraction = if (separator < 0) EMPTY else input.substring(separator + 1)
        return (whole + fraction.padEnd(FRACTION_DIGITS, ZERO)).toLongOrNull() ?: 0L
    }

    /**
     * The text the owner would have typed to reach this amount, for a record being edited.
     *
     * A round amount comes back as `25` rather than `25,00`, because that is what the keypad
     * now produces for it, and because backspace should then peel off the digits that are
     * really there. Zero comes back empty: an amount of nothing was never typed.
     */
    fun fromMinorUnits(minorUnits: Long): String {
        val magnitude = if (minorUnits < 0) -minorUnits else minorUnits
        val whole = magnitude / HUNDRED
        val cents = magnitude % HUNDRED
        return when {
            magnitude == 0L -> EMPTY
            cents == 0L -> whole.toString()
            else -> "$whole$SEPARATOR${cents.toString().padStart(FRACTION_DIGITS, ZERO)}"
        }
    }

    /** The separator this phone writes decimals with, which is what the keypad key says. */
    fun separatorFor(locale: Locale = Locale.getDefault()): Char =
        DecimalFormatSymbols.getInstance(locale).decimalSeparator

    /**
     * Canonical text as it should appear inside a text field: the same characters, with the
     * separator this phone writes. A field that showed `2.5` on a Spanish phone would be
     * telling the owner to type something the phone itself never types.
     */
    fun toTypedText(
        input: String,
        locale: Locale = Locale.getDefault(),
    ): String = input.replace(SEPARATOR, separatorFor(locale))

    /**
     * What the amount reads as while it is being typed — an echo of the keystrokes, not a
     * finished amount.
     *
     * `25` reads "25 €", `25,` reads "25, €" and `25,5` reads "25,5 €". Formatting every
     * state to two decimals instead would make the separator key and the digits after it look
     * like they did nothing, which is precisely the feedback this change came from. Grouping,
     * the symbol and which side of the number it sits on stay the locale's business.
     */
    fun format(
        input: String,
        currency: String,
        negative: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): String {
        val separator = input.indexOf(SEPARATOR)
        val typedFraction = if (separator < 0) 0 else input.length - separator - 1
        // A separator with nothing after it yet: format one decimal and drop that digit, so
        // the symbol stays on whichever side of the number this locale puts it.
        val trailing = separator >= 0 && typedFraction == 0
        val digits = input.filter { it.isDigit() }.ifEmpty { ZERO.toString() }
        val amount = BigDecimal(BigInteger(digits), typedFraction)

        val formatter =
            NumberFormat.getCurrencyInstance(locale).apply {
                runCatching { Currency.getInstance(currency) }.getOrNull()?.let { this.currency = it }
                minimumFractionDigits = if (trailing) 1 else typedFraction
                maximumFractionDigits = if (trailing) 1 else typedFraction
            }
        val text = formatter.format(amount)
        val shown = if (trailing) text.dropLastDigit() else text
        return if (negative && digits.any { it != ZERO }) MINUS + shown else shown
    }

    private fun String.dropLastDigit(): String {
        val last = indexOfLast { it.isDigit() }
        return if (last < 0) this else removeRange(last, last + 1)
    }
}

package io.github.anderescaray.cents.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The arithmetic that turns keystrokes into an integer number of cents.
 *
 * This is the whole point of the file under test: everything else about the change is
 * layout. A wrong answer here is a movement recorded for the wrong amount, so every rule the
 * owner asked for gets its own case, including the ones that must *refuse* a keystroke.
 */
class AmountEntryTest {
    private val spain = Locale.forLanguageTag("es-ES")
    private val unitedStates = Locale.forLanguageTag("en-US")

    private fun type(keys: String): String = keys.fold(AmountEntry.EMPTY, AmountEntry::append)

    /** Currency formatters use non-breaking spaces, which no assertion should care about. */
    private fun String.plainSpaces(): String = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `a plain integer is euros, not cents`() {
        // The point of the change: 25 € is these two taps, not four.
        assertEquals("25", type("25"))
        assertEquals(2_500L, AmountEntry.toMinorUnits(type("25")))
    }

    @Test
    fun `a comma and two digits are the cents`() {
        assertEquals("12.50", type("12.50"))
        assertEquals(1_250L, AmountEntry.toMinorUnits(type("12.50")))
    }

    @Test
    fun `one digit after the comma is tenths`() {
        assertEquals(250L, AmountEntry.toMinorUnits(type("2.5")))
        assertEquals(2_050L, AmountEntry.toMinorUnits(type("20.5")))
    }

    @Test
    fun `a comma with nothing behind it is still whole euros`() {
        assertEquals("2.", type("2."))
        assertEquals(200L, AmountEntry.toMinorUnits(type("2.")))
    }

    @Test
    fun `a third digit after the comma is refused rather than rounded away`() {
        assertEquals("12.50", type("12.509"))
        assertEquals(1_250L, AmountEntry.toMinorUnits(type("12.509")))
        // Not 12,51 either: the digit never arrives, so nothing rounds in any direction.
        assertEquals("12.50", type("12.5099999"))
    }

    @Test
    fun `a second comma is refused, and does not move the first`() {
        // 2,5 then a comma then 0. Moving the separator would read 25,0 — a hundred times
        // the amount, from one stray tap.
        assertEquals("2.50", type("2.5.0"))
        assertEquals(250L, AmountEntry.toMinorUnits(type("2.5.0")))
    }

    @Test
    fun `a leading comma reads as nought comma`() {
        assertEquals("0.50", type(".50"))
        assertEquals(50L, AmountEntry.toMinorUnits(type(".50")))
        assertEquals("0.", type("."))
    }

    @Test
    fun `delete removes one character, comma included`() {
        var input = type("12.50")
        assertEquals("12.5", AmountEntry.delete(input).also { input = it })
        assertEquals("12.", AmountEntry.delete(input).also { input = it })
        assertEquals("12", AmountEntry.delete(input).also { input = it })
        assertEquals("1", AmountEntry.delete(input).also { input = it })
        assertEquals(AmountEntry.EMPTY, AmountEntry.delete(input))
    }

    @Test
    fun `a digit typed after deleting the comma is euros again`() {
        val backToWhole = AmountEntry.delete(AmountEntry.delete(type("12.5")))
        assertEquals("12", backToWhole)
        assertEquals(12_300L, AmountEntry.toMinorUnits(AmountEntry.append(backToWhole, '3')))
    }

    @Test
    fun `deleting past the end of an empty entry does nothing`() {
        assertEquals(AmountEntry.EMPTY, AmountEntry.delete(AmountEntry.EMPTY))
        assertEquals(0L, AmountEntry.toMinorUnits(AmountEntry.EMPTY))
    }

    @Test
    fun `a leading zero is a placeholder rather than a digit`() {
        assertEquals("7", type("0007"))
        assertEquals(700L, AmountEntry.toMinorUnits(type("0007")))
        // But a zero that is holding the place before a comma stays put.
        assertEquals("0.05", type("0.05"))
        assertEquals(5L, AmountEntry.toMinorUnits(type("0.05")))
    }

    @Test
    fun `zeros after the comma are digits like any other`() {
        assertEquals("2.00", type("2.00"))
        assertEquals(200L, AmountEntry.toMinorUnits(type("2.00")))
        assertEquals("2.05", type("2.05"))
        assertEquals(205L, AmountEntry.toMinorUnits(type("2.05")))
    }

    @Test
    fun `the whole part is capped instead of overflowing`() {
        val absurd = AmountEntry.sanitize("9".repeat(30))
        assertEquals("999999999999", absurd)
        assertEquals(99_999_999_999_900L, AmountEntry.toMinorUnits(absurd))
    }

    @Test
    fun `sanitize keeps the number out of whatever was pasted around it`() {
        assertEquals("25.00", AmountEntry.sanitize("25,00 €"))
        assertEquals(AmountEntry.EMPTY, AmountEntry.sanitize("nonsense"))
        // A pasted, grouped number is **not** unpicked: the first separator is the decimal
        // one, always, so "1.234,56" reads as 1,23. Guessing which separator is grouping
        // would have to guess for typing too, and there it would turn a stray second comma
        // into a hundredfold error. The field shows the result, so this is visible rather
        // than silent, and nothing but a paste can reach it — the keypad has no paste.
        assertEquals("1.23", AmountEntry.sanitize("1.234,56"))
    }

    @Test
    fun `both separators are accepted, whichever key the keyboard offers`() {
        assertEquals(AmountEntry.sanitize("2,5"), AmountEntry.sanitize("2.5"))
        assertEquals(250L, AmountEntry.toMinorUnits(AmountEntry.sanitize("2,5")))
    }

    @Test
    fun `an existing amount comes back as it would have been typed`() {
        assertEquals("25", AmountEntry.fromMinorUnits(2_500))
        assertEquals("45.99", AmountEntry.fromMinorUnits(4_599))
        assertEquals("0.07", AmountEntry.fromMinorUnits(7))
        // The sign is the form's business, not the entry's.
        assertEquals("45", AmountEntry.fromMinorUnits(-4_500))
        // Nothing was typed, so nothing comes back.
        assertEquals(AmountEntry.EMPTY, AmountEntry.fromMinorUnits(0))
    }

    @Test
    fun `what comes back out types to the same number it went in as`() {
        listOf(1L, 7L, 99L, 100L, 2_500L, 4_599L, 123_456_789L).forEach { cents ->
            assertEquals(cents, AmountEntry.toMinorUnits(AmountEntry.fromMinorUnits(cents)))
        }
    }

    @Test
    fun `the display echoes the keystrokes rather than a finished amount`() {
        assertEquals("0 €", AmountEntry.format(AmountEntry.EMPTY, "EUR", locale = spain).plainSpaces())
        assertEquals("25 €", AmountEntry.format("25", "EUR", locale = spain).plainSpaces())
        // The comma key has to visibly do something, or it reads as broken.
        assertEquals("25, €", AmountEntry.format("25.", "EUR", locale = spain).plainSpaces())
        assertEquals("25,5 €", AmountEntry.format("25.5", "EUR", locale = spain).plainSpaces())
        assertEquals("25,50 €", AmountEntry.format("25.50", "EUR", locale = spain).plainSpaces())
    }

    @Test
    fun `the separator and the symbol follow the device, not the canonical text`() {
        assertEquals("$25", AmountEntry.format("25", "USD", locale = unitedStates).plainSpaces())
        assertEquals("$25.", AmountEntry.format("25.", "USD", locale = unitedStates).plainSpaces())
        assertEquals("$25.50", AmountEntry.format("25.50", "USD", locale = unitedStates).plainSpaces())
        assertEquals('.', AmountEntry.separatorFor(unitedStates))
        assertEquals(',', AmountEntry.separatorFor(spain))
    }

    @Test
    fun `grouping survives, because a six-figure amount has to stay readable`() {
        assertEquals("1.234.567 €", AmountEntry.format("1234567", "EUR", locale = spain).plainSpaces())
    }

    @Test
    fun `a negative opening balance keeps the typographic minus`() {
        assertEquals("−45 €", AmountEntry.format("45", "EUR", negative = true, locale = spain).plainSpaces())
        // Minus nothing is not a number anybody wants to look at.
        assertEquals("0 €", AmountEntry.format("0", "EUR", negative = true, locale = spain).plainSpaces())
    }

    @Test
    fun `a field shows the canonical text with this phone's separator`() {
        assertEquals("2,5", AmountEntry.toTypedText("2.5", spain))
        assertEquals("2.5", AmountEntry.toTypedText("2.5", unitedStates))
        assertEquals(AmountEntry.EMPTY, AmountEntry.toTypedText(AmountEntry.EMPTY, spain))
    }
}

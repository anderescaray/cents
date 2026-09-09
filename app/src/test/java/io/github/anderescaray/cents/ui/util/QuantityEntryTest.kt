package io.github.anderescaray.cents.ui.util

import io.github.anderescaray.cents.domain.model.Quantity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The quantity field, which is [AmountEntry]'s rules with eight decimals instead of two.
 *
 * The cases that matter are the ones where a quantity is *not* like money: a ninth decimal is
 * refused rather than rounded, and a whole number comes back without a decimal point so that
 * backspace peels off digits that were really typed.
 */
class QuantityEntryTest {
    @Test
    fun `whole units are typed the way they are said`() {
        assertEquals(Quantity.ofUnits(12), QuantityEntry.toQuantity(QuantityEntry.sanitize("12")))
    }

    @Test
    fun `a fraction is exact to the eighth decimal`() {
        val typed = QuantityEntry.sanitize("0,00000001")

        assertEquals(Quantity(1), QuantityEntry.toQuantity(typed))
    }

    @Test
    fun `a ninth decimal does not go in, rather than being rounded away`() {
        val typed = QuantityEntry.sanitize("0,000000019")

        assertEquals("0.00000001", typed)
        assertEquals(Quantity(1), QuantityEntry.toQuantity(typed))
    }

    @Test
    fun `a second separator does nothing, and a leading one reads as zero`() {
        assertEquals("2.50", QuantityEntry.sanitize("2,5,0"))
        assertEquals("0.5", QuantityEntry.sanitize(",5"))
    }

    @Test
    fun `both separators are accepted, whatever the phone writes`() {
        assertEquals(QuantityEntry.sanitize("1,5"), QuantityEntry.sanitize("1.5"))
    }

    @Test
    fun `a quantity comes back as the text that would have produced it`() {
        assertEquals("12", QuantityEntry.fromQuantity(Quantity.ofUnits(12)))
        assertEquals("0.5", QuantityEntry.fromQuantity(Quantity(Quantity.SCALE / 2)))
        assertEquals("", QuantityEntry.fromQuantity(Quantity.ZERO))
    }

    @Test
    fun `reading a quantity keeps the decimals it has and invents none`() {
        assertEquals("12", QuantityEntry.format(Quantity.ofUnits(12), Locale.UK))
        assertEquals("0.5", QuantityEntry.format(Quantity(Quantity.SCALE / 2), Locale.UK))
        assertEquals("0.00000001", QuantityEntry.format(Quantity(1), Locale.UK))
    }

    @Test
    fun `the money field is unchanged by sharing its rules`() {
        // AmountEntry and QuantityEntry now run the same loop with different limits, so this
        // is the guard that the money one still stops at two decimals.
        assertEquals("12.50", AmountEntry.sanitize("12,509"))
    }
}

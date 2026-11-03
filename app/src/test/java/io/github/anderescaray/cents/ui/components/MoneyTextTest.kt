package io.github.anderescaray.cents.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTextTest {
    private fun whole(
        text: String,
        separator: Char,
    ): String? = wholeUnits(text, separator)?.let { text.substring(it.first, it.last + 1) }

    @Test
    fun `a Spanish figure keeps its thousands and drops the cents and the euro`() {
        assertEquals("1.634", whole("1.634,40 €", ','))
    }

    @Test
    fun `an English figure drops the leading symbol and the cents`() {
        assertEquals("1,634", whole("€1,634.40", '.'))
    }

    @Test
    fun `a sign at the start stays with the number`() {
        assertEquals("−1.634", whole("−1.634,40 €", ','))
        assertEquals("+12", whole("+12,05 €", ','))
    }

    @Test
    fun `a currency without cents keeps every digit`() {
        assertEquals("1,634", whole("¥1,634", '.'))
    }

    @Test
    fun `text without digits has no whole units`() {
        assertNull(wholeUnits("€", ','))
    }
}

class ShareOfTest {
    @Test
    fun `a part is its fraction of the whole`() {
        assertEquals(0.25f, shareOf(1_000, 3_000)!!, 0.0001f)
    }

    @Test
    fun `nothing to share, or money below zero, has no share`() {
        assertNull(shareOf(0, 0))
        assertNull(shareOf(-500, 3_000))
        assertNull(shareOf(500, -3_000))
    }
}

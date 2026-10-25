package io.github.anderescaray.cents.ui.feature.categories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the category's emoji field keeps of what was typed: one emoji, whole. */
class SingleEmojiTest {
    @Test
    fun `one emoji is kept as typed`() {
        assertEquals("🛒", singleEmoji("🛒"))
    }

    @Test
    fun `typing a second one replaces the first`() {
        assertEquals("🍕", singleEmoji("🛒🍕"))
    }

    @Test
    fun `an emoji of several code points is kept whole`() {
        assertEquals("👍🏽", singleEmoji("👍🏽"))
        assertEquals("👨‍👩‍👧", singleEmoji("👨‍👩‍👧"))
        assertEquals("🍽️", singleEmoji("🍽️"))
    }

    @Test
    fun `nothing left clears it`() {
        assertNull(singleEmoji(""))
        assertNull(singleEmoji("  "))
    }
}

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
    fun `a letter or a digit is not taken for an emoji`() {
        assertEquals("🛒", nextEmoji(current = "🛒", typed = "🛒d"))
        assertNull(nextEmoji(current = null, typed = "Food"))
        assertNull(nextEmoji(current = null, typed = "7"))
    }

    @Test
    fun `a new emoji wins wherever the caret put it`() {
        assertEquals("🍔", nextEmoji(current = "🍕", typed = "🍔🍕"))
        assertEquals("🍔", nextEmoji(current = "🍕", typed = "🍕🍔"))
        assertNull(nextEmoji(current = "🍕", typed = ""))
    }

    @Test
    fun `nothing left clears it`() {
        assertNull(singleEmoji(""))
        assertNull(singleEmoji("  "))
    }
}

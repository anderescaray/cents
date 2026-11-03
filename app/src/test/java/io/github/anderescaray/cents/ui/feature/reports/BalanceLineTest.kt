package io.github.anderescaray.cents.ui.feature.reports

import io.github.anderescaray.cents.ui.feature.reports.section.indexAt
import io.github.anderescaray.cents.ui.feature.reports.section.yBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceLineTest {
    @Test
    fun `a finger reads the nearest day, and never one past either end`() {
        assertEquals(0, indexAt(x = 0f, width = 300f, count = 31))
        assertEquals(15, indexAt(x = 150f, width = 300f, count = 31))
        assertEquals(30, indexAt(x = 300f, width = 300f, count = 31))
        assertEquals(30, indexAt(x = 999f, width = 300f, count = 31))
        assertEquals(0, indexAt(x = -20f, width = 300f, count = 31))
    }

    @Test
    fun `the axis leaves room around a moving line`() {
        val (low, high) = yBounds(listOf(100_000L, 97_201L))
        assertTrue(low < 97_201L)
        assertTrue(high > 100_000L)
    }

    @Test
    fun `a line that never moved sits inside a band, not on an edge`() {
        val (low, high) = yBounds(listOf(50_000L, 50_000L))
        assertTrue(low < 50_000L && high > 50_000L)
        assertEquals(50_000L - low, high - 50_000L)
    }
}

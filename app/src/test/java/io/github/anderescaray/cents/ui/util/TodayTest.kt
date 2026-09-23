package io.github.anderescaray.cents.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TodayTest {
    private var now = Instant.parse("2026-08-31T23:59:00Z")
    private val clock =
        object : Clock() {
            override fun instant(): Instant = now

            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        }

    @Test
    fun `it starts on the clock's day`() {
        assertEquals(LocalDate.of(2026, 8, 31), Today(clock).date.value)
    }

    @Test
    fun `a refresh within the same day changes nothing`() {
        val today = Today(clock)
        val before = today.date.value
        now = Instant.parse("2026-08-31T23:59:59Z")

        today.refresh()

        // The same instance, so a StateFlow has nothing to emit and no screen rebuilds a query.
        assertSame(before, today.date.value)
    }

    @Test
    fun `a refresh after midnight moves to the new day`() {
        val today = Today(clock)
        now = Instant.parse("2026-09-01T00:00:01Z")

        today.refresh()

        assertEquals(LocalDate.of(2026, 9, 1), today.date.value)
    }

    @Test
    fun `the day is the clock's zone's day, not UTC's`() {
        // 23:30 UTC on the 31st is already the 1st in Madrid, in summer.
        val madrid = Clock.fixed(Instant.parse("2026-08-31T23:30:00Z"), ZoneId.of("Europe/Madrid"))
        assertEquals(LocalDate.of(2026, 9, 1), Today(madrid).date.value)
    }
}

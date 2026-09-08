package io.github.anderescaray.cents.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A layout is arranged on one screen and read on another — a rotation, a split screen, a new
 * phone. These pin what happens to a bubble that no longer fits where it was put.
 */
class BubblePlacementTest {
    @Test
    fun `a bubble already inside the canvas is left exactly as it was`() {
        val placement = BubblePlacement(centerX = 0.5f, centerY = 0.5f, diameter = 0.3f)

        assertEquals(placement, placement.fittedTo(PORTRAIT))
    }

    @Test
    fun `a bubble over the edge is pushed back in, whole`() {
        val fitted = BubblePlacement(centerX = 0.98f, centerY = 0.02f, diameter = 0.3f).fittedTo(PORTRAIT)

        assertEquals(1f - 0.15f, fitted.centerX, TOLERANCE)
        assertTrue("its top edge should be on the canvas", fitted.centerY >= 0.15f * PORTRAIT)
    }

    @Test
    fun `a bubble bigger than the canvas allows is shrunk rather than cropped`() {
        val fitted = BubblePlacement(centerX = 0.5f, centerY = 0.5f, diameter = 5f).fittedTo(PORTRAIT)

        assertEquals(BubblePlacement.MAX_DIAMETER, fitted.diameter, TOLERANCE)
    }

    @Test
    fun `a bubble smaller than a tap target is grown back to one`() {
        val fitted = BubblePlacement(centerX = 0.5f, centerY = 0.5f, diameter = 0.01f).fittedTo(PORTRAIT)

        assertEquals(BubblePlacement.MIN_DIAMETER, fitted.diameter, TOLERANCE)
    }

    @Test
    fun `no canvas shape, however extreme, can be asked for an impossible position`() {
        // Measuring the diameter on the shorter side is what makes a bubble always fit, so the
        // centring guard should never fire — but "should never" plus a coerceIn whose range can
        // invert is a crash, and a launcher that crashes on a stretched window is a dead app.
        for (aspect in listOf(0.02f, PORTRAIT, 1f, LANDSCAPE, VERY_WIDE, 50f)) {
            val fitted = BubblePlacement(centerX = -3f, centerY = 4f, diameter = 0.62f).fittedTo(aspect)

            assertTrue("aspect $aspect put it at ${fitted.centerX}", fitted.centerX in 0f..1f)
            assertTrue("aspect $aspect put it at ${fitted.centerY}", fitted.centerY in 0f..1f)
        }
    }

    @Test
    fun `fitting is settled - fitting a fitted placement changes nothing`() {
        for (aspect in listOf(PORTRAIT, LANDSCAPE, VERY_WIDE)) {
            val once = BubblePlacement(centerX = 1.4f, centerY = -0.3f, diameter = 0.9f).fittedTo(aspect)

            assertEquals("aspect $aspect", once, once.fittedTo(aspect))
        }
    }

    private companion object {
        /** width ÷ height of a phone held upright, on its side, and of a wide window. */
        const val PORTRAIT = 1080f / 2400f
        const val LANDSCAPE = 2400f / 1080f
        const val VERY_WIDE = 4f
        const val TOLERANCE = 0.0001f
    }
}

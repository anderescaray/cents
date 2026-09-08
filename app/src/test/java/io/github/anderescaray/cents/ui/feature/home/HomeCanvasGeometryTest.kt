package io.github.anderescaray.cents.ui.feature.home

import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arrangement a fresh install opens on. It is the first thing anyone sees of the app, and
 * nothing at runtime corrects it — bubbles may overlap once they are dragged, but they must not
 * start that way.
 */
class HomeCanvasGeometryTest {
    @Test
    fun `every section starts somewhere of its own`() {
        val placements = TopLevelTab.entries.mapIndexed { index, _ -> defaultPlacement(index) }

        assertEquals(placements.size, placements.distinct().size)
    }

    @Test
    fun `the four start clear of each other on a phone held upright`() {
        val placements = TopLevelTab.entries.mapIndexed { index, _ -> defaultPlacement(index) }

        for (i in placements.indices) {
            for (j in i + 1 until placements.size) {
                val clearance = clearanceBetween(placements[i], placements[j], PORTRAIT)
                assertTrue(
                    "bubbles $i and $j are $clearance apart, in diameters",
                    clearance > MIN_CLEARANCE,
                )
            }
        }
    }

    @Test
    fun `they start whole and inside the canvas, upright or on its side`() {
        for (aspect in listOf(PORTRAIT, LANDSCAPE)) {
            TopLevelTab.entries.forEachIndexed { index, tab ->
                val placement = defaultPlacement(index)

                assertEquals("$tab at aspect $aspect", placement, placement.fittedTo(aspect))
            }
        }
    }

    @Test
    fun `a fifth section would be placed rather than crash`() {
        // Nothing offers one today, and the canvas should not be what stops it.
        assertEquals(defaultPlacement(0), defaultPlacement(4))
    }

    private companion object {
        const val PORTRAIT = 1080f / 2400f
        const val LANDSCAPE = 2400f / 1080f

        /** A tenth of a diameter of air, so they read as separate rather than as touching. */
        const val MIN_CLEARANCE = 0.1f
    }
}

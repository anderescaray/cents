package io.github.anderescaray.cents.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which tab a sideways swipe leads to, and that it slides the way the finger went. */
class TabSwipeTest {
    @Test
    fun `right to left goes to the next tab, left to right to the one before`() {
        assertEquals(TopLevelTab.TRANSACTIONS, swipeTarget(TopLevelTab.HOME, towardsNext = true))
        assertEquals(TopLevelTab.REPORTS, swipeTarget(TopLevelTab.ACCOUNTS, towardsNext = false))
    }

    @Test
    fun `nothing lies past either end`() {
        assertNull(swipeTarget(TopLevelTab.HOME, towardsNext = false))
        assertNull(swipeTarget(TopLevelTab.ACCOUNTS, towardsNext = true))
    }

    @Test
    fun `the new tab slides in from the side the finger came from`() {
        val next = swipeTarget(TopLevelTab.HOME, towardsNext = true)
        assertEquals(SlideDirection.Start, slideDirectionFor(from = TopLevelTab.HOME, to = next))
        val previous = swipeTarget(TopLevelTab.REPORTS, towardsNext = false)
        assertEquals(SlideDirection.End, slideDirectionFor(from = TopLevelTab.REPORTS, to = previous))
    }
}

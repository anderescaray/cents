package io.github.anderescaray.cents.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import io.github.anderescaray.cents.ui.navigation.routes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tab bar's animation used to claim you had moved forward whichever way you went, because
 * a tab switch is a `navigate()` and every `navigate()` is a push. These pin the rule that
 * replaced the single direction — the two cases in the report are the first two tests.
 */
class TabTravelTest {
    @Test
    fun `Reports back to Accounts arrives from the side it left towards`() {
        assertEquals(
            SlideDirection.End,
            slideDirectionFor(from = TopLevelTab.REPORTS, to = TopLevelTab.ACCOUNTS),
        )
    }

    @Test
    fun `Settings back to Reports arrives from the same side`() {
        assertEquals(
            SlideDirection.End,
            slideDirectionFor(from = TopLevelTab.SETTINGS, to = TopLevelTab.REPORTS),
        )
    }

    @Test
    fun `moving right along the tabs still pushes forward`() {
        assertEquals(
            SlideDirection.Start,
            slideDirectionFor(from = TopLevelTab.TRANSACTIONS, to = TopLevelTab.REPORTS),
        )
    }

    @Test
    fun `every pair of tabs disagrees with its reverse`() {
        for (from in TopLevelTab.entries) {
            for (to in TopLevelTab.entries) {
                if (from == to) continue
                assertEquals(
                    "$from to $to should be the opposite of $to to $from",
                    slideDirectionFor(from, to) == SlideDirection.Start,
                    slideDirectionFor(to, from) == SlideDirection.End,
                )
            }
        }
    }

    @Test
    fun `switching into a tab whose stack has a detail screen on top travels backwards`() {
        // What arrives is an account's detail screen, but the tab you moved to is Accounts, and
        // that is what the direction is owed to. Before the tab owned its pushed screens there
        // was nothing to compare here and this slid forward — the last case #31 left open.
        val arriving = TopLevelTab.entries.first { Destination.AccountDetail::class in it.routes }
        assertEquals(
            SlideDirection.End,
            slideDirectionFor(from = TopLevelTab.SETTINGS, to = arriving),
        )
    }

    @Test
    fun `pushing a screen no tab owns is a push, whichever tab it is from`() {
        // The movement form opens from two tabs, so it is owned by neither: there are no
        // positions to compare and the forward slide is what a push should look like.
        for (tab in TopLevelTab.entries) {
            assertEquals(SlideDirection.Start, slideDirectionFor(from = tab, to = null))
            assertEquals(SlideDirection.Start, slideDirectionFor(from = null, to = tab))
        }
        assertEquals(SlideDirection.Start, slideDirectionFor(from = null, to = null))
    }

    @Test
    fun `going nowhere is not going backwards`() {
        for (tab in TopLevelTab.entries) {
            assertEquals(SlideDirection.Start, slideDirectionFor(from = tab, to = tab))
        }
    }
}

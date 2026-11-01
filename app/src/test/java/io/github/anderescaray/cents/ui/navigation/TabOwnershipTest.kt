package io.github.anderescaray.cents.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/**
 * What each tab answers for. The tab bar used to light *Transactions* over any pushed screen
 * and a tab switch used to slide forwards into a saved stack with a detail screen on top, both
 * because a destination carried no record of the tab it belongs to. These pin the record.
 *
 * The half not tested here is `hasRoute`, which is androidx's: what is ours is the declaration,
 * and it is a list rather than a graph precisely so it can be read on the JVM.
 */
class TabOwnershipTest {
    @Test
    fun `an account's detail screen belongs to Accounts`() {
        assertEquals(TopLevelTab.ACCOUNTS, tabOwning(Destination.AccountDetail::class))
    }

    @Test
    fun `the filter form belongs to Transactions`() {
        assertEquals(TopLevelTab.TRANSACTIONS, tabOwning(Destination.TransactionFilters::class))
    }

    @Test
    fun `the category list belongs to Home, where Settings opens from`() {
        assertEquals(TopLevelTab.HOME, tabOwning(Destination.Categories::class))
    }

    @Test
    fun `budgets belong to Reports`() {
        assertEquals(TopLevelTab.REPORTS, tabOwning(Destination.Budgets::class))
    }

    @Test
    fun `every tab answers for itself`() {
        for (tab in TopLevelTab.entries) {
            assertEquals(tab, tabOwning(tab.destination::class))
        }
    }

    @Test
    fun `a screen two tabs can reach is claimed by neither`() {
        // The movement form opens from the list and from an account, and the transfer form
        // from both of those and from the movement form; the account editor from Accounts,
        // from an account's detail and from the form; the category editor from Settings and
        // from the form. Naming an owner for any of them would pick one of two
        // truths, so the tab the owner was already in is what stands.
        assertEquals(null, tabOwning(Destination.TransactionForm::class))
        assertEquals(null, tabOwning(Destination.TransferForm::class))
        assertEquals(null, tabOwning(Destination.AccountEdit::class))
        assertEquals(null, tabOwning(Destination.CategoryEdit::class))
    }

    @Test
    fun `no route is claimed by two tabs`() {
        val claimed = TopLevelTab.entries.flatMap { it.routes }
        assertEquals(
            "a route answered for by two tabs would make the bar and the slide disagree",
            claimed.distinct(),
            claimed,
        )
    }

    @Test
    fun `no tab owns another tab's root`() {
        val roots = TopLevelTab.entries.map { it.destination::class }
        for (tab in TopLevelTab.entries) {
            assertTrue(
                "$tab owns a tab root, which would hide that tab from the bar",
                tab.owns.none { it in roots },
            )
        }
    }

    /** The lookup [owningTab][io.github.anderescaray.cents.ui.owningTab] makes, minus `hasRoute`. */
    private fun tabOwning(route: KClass<out Destination>): TopLevelTab? =
        TopLevelTab.entries.firstOrNull { route in it.routes }
}

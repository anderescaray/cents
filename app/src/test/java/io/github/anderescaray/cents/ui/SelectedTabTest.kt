package io.github.anderescaray.cents.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The tab the bar lights, driven by a real back stack rather than by a table of routes — this is
 * where `hasRoute` is actually asked, and a route carrying an argument (`AccountDetail(1)`) is
 * the case that would fail if the lookup only matched bare objects.
 *
 * Tab taps go through the app's own [switchToTab], because saving and restoring a tab's stack is
 * exactly what the answer has to survive: a screen no tab owns can be restored under a tab the
 * owner left several taps ago.
 *
 * The graph is a stand-in for the app's: real destinations, empty screens, so the test is about
 * navigation and not about what a screen renders.
 */
@RunWith(RobolectricTestRunner::class)
class SelectedTabTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController
    private var selected: TopLevelTab? = null

    @Test
    fun `the app opens on Home, and the capsule lights it`() {
        start()

        assertEquals(TopLevelTab.HOME, selected)
    }

    @Test
    fun `an account's detail screen keeps the bar on Accounts`() {
        start()

        switchTo(TopLevelTab.ACCOUNTS)
        assertEquals(TopLevelTab.ACCOUNTS, selected)

        push(Destination.AccountDetail(accountId = 1))
        // It used to fall back to the first tab here, because a pushed destination matched no
        // tab at all — the bar said Transactions while an account was open.
        assertEquals(TopLevelTab.ACCOUNTS, selected)
    }

    @Test
    fun `a movement opened from an account leaves you in Accounts`() {
        start()

        switchTo(TopLevelTab.ACCOUNTS)
        push(Destination.AccountDetail(accountId = 1))
        push(Destination.TransactionForm(transactionId = 7))

        // The form belongs to no tab, so what it is standing on decides: the account's detail.
        assertEquals(TopLevelTab.ACCOUNTS, selected)
    }

    @Test
    fun `the same form opened from the list leaves you in Transactions`() {
        start()

        switchTo(TopLevelTab.ACCOUNTS)
        switchTo(TopLevelTab.TRANSACTIONS)
        push(Destination.TransactionForm(transactionId = 7))

        assertEquals(TopLevelTab.TRANSACTIONS, selected)
    }

    @Test
    fun `coming back to a tab whose form was left open lands on that tab, not the one you left`() {
        start()

        switchTo(TopLevelTab.ACCOUNTS)
        push(Destination.AccountDetail(accountId = 1))
        push(Destination.TransactionForm(transactionId = 7))

        switchTo(TopLevelTab.REPORTS)
        assertEquals(TopLevelTab.REPORTS, selected)

        // Accounts restores its saved stack, form and all. A single remembered "last owned tab"
        // would still be saying Reports here — the tab the owner had just left.
        switchTo(TopLevelTab.ACCOUNTS)
        assertEquals(TopLevelTab.ACCOUNTS, selected)
    }

    @Test
    fun `back from a tab root returns home rather than leaving the app`() {
        start()

        switchTo(TopLevelTab.REPORTS)
        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()

        // Every tab sits on Home, which is what makes back mean something from a tab root.
        assertEquals(TopLevelTab.HOME, selected)
    }

    @Test
    fun `settings and the category list behind it belong to Home, where Settings opens from`() {
        start()

        switchTo(TopLevelTab.REPORTS)
        switchTo(TopLevelTab.HOME)
        push(Destination.Settings)
        push(Destination.Categories)
        push(Destination.CategoryEdit(categoryId = 3))

        assertEquals(TopLevelTab.HOME, selected)
    }

    @Test
    fun `budgets belong to Reports now`() {
        start()

        switchTo(TopLevelTab.REPORTS)
        push(Destination.Budgets)

        assertEquals(TopLevelTab.REPORTS, selected)
    }

    private fun start() {
        composeRule.setContent {
            navController = rememberNavController()
            val backStack by navController.currentBackStack.collectAsState()
            val tab = backStack.selectedTab()
            SideEffect { selected = tab }

            NavHost(navController = navController, startDestination = Destination.Home) {
                composable<Destination.Home> {}
                composable<Destination.Transactions> {}
                composable<Destination.Accounts> {}
                composable<Destination.Reports> {}
                composable<Destination.Settings> {}
                composable<Destination.AccountDetail> {}
                composable<Destination.Categories> {}
                composable<Destination.CategoryEdit> {}
                composable<Destination.TransactionForm> {}
                composable<Destination.Budgets> {}
            }
        }
        composeRule.waitForIdle()
    }

    /** What the tab bar does, saved stacks and all. */
    private fun switchTo(tab: TopLevelTab) {
        composeRule.runOnIdle { navController.switchToTab(tab.destination) }
        composeRule.waitForIdle()
    }

    private fun push(destination: Destination) {
        composeRule.runOnIdle { navController.navigate(destination) }
        composeRule.waitForIdle()
    }
}

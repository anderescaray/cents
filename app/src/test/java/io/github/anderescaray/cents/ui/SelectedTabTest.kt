package io.github.anderescaray.cents.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The tab the bar lights, driven by a real back stack rather than by a table of routes — this
 * is where `hasRoute` is actually asked, and a route carrying an argument
 * (`AccountDetail(1)`) is the case that would fail if the lookup only matched bare objects.
 *
 * The graph here is a stand-in for the app's: real destinations, empty screens, so the test is
 * about navigation and not about what a screen renders.
 */
@RunWith(RobolectricTestRunner::class)
class SelectedTabTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController
    private var selected: TopLevelTab? = null

    @Test
    fun `an account's detail screen keeps the bar on Accounts`() {
        start()

        navigate(Destination.Accounts)
        assertEquals(TopLevelTab.ACCOUNTS, selected)

        navigate(Destination.AccountDetail(accountId = 1))
        // It used to fall back to the first tab here, because a pushed destination matched no
        // tab at all — the bar said Transactions while an account was open.
        assertEquals(TopLevelTab.ACCOUNTS, selected)
    }

    @Test
    fun `a movement opened from an account leaves you in Accounts`() {
        start()

        navigate(Destination.Accounts)
        navigate(Destination.AccountDetail(accountId = 1))
        navigate(Destination.TransactionForm(transactionId = 7))

        // The form belongs to no tab, so the tab you were in stands. The same screen reached
        // from the list answers Transactions, which is the next test.
        assertEquals(TopLevelTab.ACCOUNTS, selected)
    }

    @Test
    fun `the same form opened from the list leaves you in Transactions`() {
        start()

        navigate(Destination.TransactionForm(transactionId = 7))

        assertEquals(TopLevelTab.TRANSACTIONS, selected)
    }

    @Test
    fun `the category list belongs to Settings, even reached from a tab away`() {
        start()

        navigate(Destination.Settings)
        navigate(Destination.Categories)

        assertEquals(TopLevelTab.SETTINGS, selected)
    }

    @Test
    fun `the remembered tab is saved by name and comes back`() {
        // What the back stack cannot say after process death: it restores an unowned screen
        // under whichever tab it was pushed from, and only this remembers which that was.
        val scope = SaverScope { true }
        for (tab in TopLevelTab.entries) {
            val saved = with(TabSaver) { with(scope) { save(tab) } }
            assertEquals(tab, saved?.let(TabSaver::restore))
        }
    }

    @Test
    fun `a tab that no longer exists restores as nothing rather than as a crash`() {
        assertEquals(null, TabSaver.restore("ACCOUNTS_RENAMED"))
    }

    private fun start() {
        composeRule.setContent {
            navController = rememberNavController()
            val entry by navController.currentBackStackEntryAsState()
            val tab = rememberSelectedTab(entry?.destination)
            SideEffect { selected = tab }

            NavHost(navController = navController, startDestination = Destination.Transactions) {
                composable<Destination.Transactions> {}
                composable<Destination.Accounts> {}
                composable<Destination.Settings> {}
                composable<Destination.AccountDetail> {}
                composable<Destination.Categories> {}
                composable<Destination.TransactionForm> {}
            }
        }
        composeRule.waitForIdle()
    }

    private fun navigate(destination: Destination) {
        composeRule.runOnIdle { navController.navigate(destination) }
        composeRule.waitForIdle()
    }
}

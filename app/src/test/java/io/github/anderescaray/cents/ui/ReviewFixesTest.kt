package io.github.anderescaray.cents.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.anderescaray.cents.domain.model.ReportLayout
import io.github.anderescaray.cents.domain.repository.ReportLayoutRepository
import io.github.anderescaray.cents.ui.components.between
import io.github.anderescaray.cents.ui.feature.ViewModelKeeper
import io.github.anderescaray.cents.ui.feature.reports.ReportCard
import io.github.anderescaray.cents.ui.feature.reports.ReportLayoutViewModel
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What `/code-review high` of #109 found, each pinned so it cannot come back. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewFixesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val viewModels = ViewModelKeeper()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        try {
            viewModels.cancelAll()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a counting figure rests on the exact amount, however large the change`() {
        // 20 000 001 cents is not a Float: through one it became 20 000 000, one cent short.
        assertEquals(35_000_001L, between(from = 15_000_000L, target = 35_000_001L, progress = 1f))
        assertEquals(25_000_001L, between(from = 15_000_000L, target = 35_000_002L, progress = 0.5f))
        assertEquals(15_000_000L, between(from = 15_000_000L, target = 35_000_001L, progress = 0f))
    }

    @Test
    fun `the tab already open goes back to its first screen`() {
        lateinit var nav: NavHostController
        composeRule.setContent { nav = rememberNavController().also { Graph(it) } }

        composeRule.runOnIdle { nav.navigate(Destination.Settings) }
        composeRule.runOnIdle { nav.selectTab(TopLevelTab.REPORTS, current = TopLevelTab.HOME) }
        // Back to Home: the tab keeps where it was left, Settings on top, as iOS does.
        composeRule.runOnIdle { nav.selectTab(TopLevelTab.HOME, current = TopLevelTab.REPORTS) }
        composeRule.runOnIdle {
            assertEquals(listOf("Home", "Settings"), nav.routes())
            // And again: this is the tap that did nothing, leaving quick add out of reach.
            nav.selectTab(TopLevelTab.HOME, current = TopLevelTab.HOME)
        }
        composeRule.runOnIdle { assertEquals(listOf("Home"), nav.routes()) }
    }

    @Test
    fun `two quick changes to the report arrangement both stick, and nothing is drawn before it is read`() =
        runTest {
            val repository = StoredLayout(ReportLayout(hidden = setOf(ReportCard.entries.last().key)))
            val viewModel = viewModels.keep(ReportLayoutViewModel(repository))
            assertFalse("not read yet", viewModel.uiState.value.loaded)

            // Neither waits for the other, nor for the stored arrangement to be read.
            val first = ReportCard.entries.first()
            val second = ReportCard.entries[1]
            viewModel.setVisible(first, visible = false)
            viewModel.setVisible(second, visible = false)

            val saved = repository.stored.first { second.key in it.hidden }
            assertTrue("the first change survived the second", first.key in saved.hidden)
            assertTrue("the arrangement already stored survived both", ReportCard.entries.last().key in saved.hidden)
            assertTrue(viewModel.uiState.first { it.loaded }.loaded)
        }

    /** The routes on the back stack, by the name of their destination. */
    private fun NavHostController.routes(): List<String> =
        currentBackStack.value
            .mapNotNull { it.destination.route }
            .map { route -> route.substringAfterLast('.').substringBefore('/').substringBefore('?') }

    @Composable
    private fun Graph(nav: NavHostController) {
        NavHost(navController = nav, startDestination = Destination.Home) {
            composable<Destination.Home> {}
            composable<Destination.Settings> {}
            composable<Destination.Reports> {}
            composable<Destination.Transactions> {}
        }
    }

    /** A stored arrangement that, like DataStore, only changes once a save completes. */
    private class StoredLayout(
        initial: ReportLayout,
    ) : ReportLayoutRepository {
        val stored = MutableStateFlow(initial)

        override fun observe(): Flow<ReportLayout> = stored

        override suspend fun save(layout: ReportLayout) {
            stored.value = layout
        }
    }
}

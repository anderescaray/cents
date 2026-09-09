package io.github.anderescaray.cents.ui.feature.home

import app.cash.turbine.test
import io.github.anderescaray.cents.domain.model.BubblePlacement
import io.github.anderescaray.cents.domain.model.HomeLayout
import io.github.anderescaray.cents.domain.repository.HomeLayoutRepository
import io.github.anderescaray.cents.ui.feature.ViewModelKeeper
import io.github.anderescaray.cents.ui.feature.awaitStateThat
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val layouts = FakeHomeLayoutRepository()
    private val viewModels = ViewModelKeeper()
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = viewModels.keep(HomeViewModel(layouts))
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
    fun `every section gets a bubble, whether or not it was ever moved`() =
        runTest {
            layouts.layout.update {
                it.copy(placements = mapOf(TopLevelTab.REPORTS.name to MOVED))
            }

            viewModel.state.test {
                val state = awaitStateThat { it.loaded }

                assertEquals(TopLevelTab.entries.size, state.bubbles.size)
                assertEquals(TopLevelTab.entries, state.bubbles.map { it.tab })
                assertEquals(MOVED, state.bubbles.first { it.tab == TopLevelTab.REPORTS }.placement)
                assertEquals(
                    "a section nobody moved sits where a fresh install puts it",
                    defaultPlacement(TopLevelTab.ACCOUNTS.ordinal),
                    state.bubbles.first { it.tab == TopLevelTab.ACCOUNTS }.placement,
                )
            }
        }

    @Test
    fun `a stored section that is no longer a tab reads as no section at all`() =
        runTest {
            // The name here has to be one no tab answers to. It used to be "INVESTMENTS",
            // which stopped working the day investments became a section — which is the exact
            // scenario this test exists for, arriving from the other direction.
            layouts.layout.update { it.copy(lastSection = "BUDGETS") }

            viewModel.state.test {
                assertNull(awaitStateThat { it.loaded }.lastSection)
            }
        }

    @Test
    fun `the section that was open is the one the app reopens on`() =
        runTest {
            layouts.layout.update { it.copy(lastSection = TopLevelTab.SETTINGS.name) }

            viewModel.state.test {
                assertEquals(TopLevelTab.SETTINGS, awaitStateThat { it.loaded }.lastSection)
            }
        }

    @Test
    fun `moving a bubble stores it under its own section`() =
        runTest {
            viewModel.move(TopLevelTab.ACCOUNTS, MOVED)

            assertEquals(MOVED, layouts.layout.value.placements[TopLevelTab.ACCOUNTS.name])
        }

    @Test
    fun `resetting clears what was arranged and nothing else`() =
        runTest {
            viewModel.move(TopLevelTab.ACCOUNTS, MOVED)
            viewModel.setTabBarCollapsed(true)

            viewModel.resetLayout()

            assertEquals(emptyMap<String, BubblePlacement>(), layouts.layout.value.placements)
            assertEquals(true, layouts.layout.value.tabBarCollapsed)
        }

    @Test
    fun `being on the canvas is remembered as being in no section`() =
        runTest {
            viewModel.rememberSection(TopLevelTab.REPORTS)
            viewModel.rememberSection(null)

            assertNull(layouts.layout.value.lastSection)
        }

    private companion object {
        val MOVED = BubblePlacement(centerX = 0.42f, centerY = 0.24f, diameter = 0.3f)
    }
}

private class FakeHomeLayoutRepository : HomeLayoutRepository {
    val layout = MutableStateFlow(HomeLayout.Untouched)

    override fun observe(): Flow<HomeLayout> = layout

    override suspend fun place(
        section: String,
        placement: BubblePlacement,
    ) {
        layout.update { it.copy(placements = it.placements + (section to placement)) }
    }

    override suspend fun setTabBarCollapsed(collapsed: Boolean) {
        layout.update { it.copy(tabBarCollapsed = collapsed) }
    }

    override suspend fun setLastSection(section: String?) {
        layout.update { it.copy(lastSection = section) }
    }

    override suspend fun resetPlacements() {
        layout.update { it.copy(placements = emptyMap()) }
    }
}

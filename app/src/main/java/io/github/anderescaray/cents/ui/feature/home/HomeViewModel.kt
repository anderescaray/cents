package io.github.anderescaray.cents.ui.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.BubblePlacement
import io.github.anderescaray.cents.domain.model.HomeLayout
import io.github.anderescaray.cents.domain.repository.HomeLayoutRepository
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One section, as a bubble on the canvas. */
@Immutable
data class HomeBubble(
    val tab: TopLevelTab,
    val placement: BubblePlacement,
)

/**
 * [loaded] separates "the owner has moved nothing" from "the store has not answered yet": the
 * two look identical and drawing the second as the first makes the bubbles jump into place a
 * frame after they appear.
 */
@Immutable
data class HomeUiState(
    val bubbles: List<HomeBubble> = emptyList(),
    val tabBarCollapsed: Boolean = false,
    val lastSection: TopLevelTab? = null,
    val loaded: Boolean = false,
)

/**
 * The canvas and the shell around it: where the bubbles sit, whether the tab bar is showing,
 * and which section the app should reopen on.
 *
 * One view model for both, held by `CentsApp` and handed to the canvas, because they are two
 * views of one arrangement — a second instance would be a second copy of the same truth, and
 * the one that wrote last would win.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val layouts: HomeLayoutRepository,
    ) : ViewModel() {
        val state: StateFlow<HomeUiState> =
            layouts
                .observe()
                .map { it.toUiState() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = HomeUiState(),
                )

        fun move(
            tab: TopLevelTab,
            placement: BubblePlacement,
        ) {
            viewModelScope.launch { layouts.place(tab.name, placement) }
        }

        fun setTabBarCollapsed(collapsed: Boolean) {
            viewModelScope.launch { layouts.setTabBarCollapsed(collapsed) }
        }

        /** Null while the canvas itself is open, so a restart lands there rather than in a section. */
        fun rememberSection(tab: TopLevelTab?) {
            viewModelScope.launch { layouts.setLastSection(tab?.name) }
        }

        fun resetLayout() {
            viewModelScope.launch { layouts.resetPlacements() }
        }

        /**
         * A section with nothing saved gets the arrangement a fresh install has, and a saved
         * section that is no longer a tab is dropped: the layout is addressed by name, so a
         * renamed one has to read as "never moved" rather than as an error.
         */
        private fun HomeLayout.toUiState(): HomeUiState =
            HomeUiState(
                bubbles =
                    TopLevelTab.entries.mapIndexed { index, tab ->
                        HomeBubble(tab = tab, placement = placements[tab.name] ?: defaultPlacement(index))
                    },
                tabBarCollapsed = tabBarCollapsed,
                lastSection = TopLevelTab.entries.firstOrNull { it.name == lastSection },
                loaded = true,
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.BubblePlacement
import io.github.anderescaray.cents.domain.model.HomeLayout
import kotlinx.coroutines.flow.Flow

/**
 * The home canvas as the owner left it. A `Flow`, like everything else that is stored, so the
 * screen follows a write instead of being told to reload after one.
 *
 * Sections are addressed by a stable key rather than by a UI type, because what a tab *is*
 * belongs to `ui/navigation` and this layer does not import it.
 */
interface HomeLayoutRepository {
    fun observe(): Flow<HomeLayout>

    suspend fun place(
        section: String,
        placement: BubblePlacement,
    )

    suspend fun setTabBarCollapsed(collapsed: Boolean)

    /** Null when the owner is on the canvas itself, so reopening does not land in a section. */
    suspend fun setLastSection(section: String?)

    /** Back to the arrangement a fresh install has, positions and sizes both. */
    suspend fun resetPlacements()
}

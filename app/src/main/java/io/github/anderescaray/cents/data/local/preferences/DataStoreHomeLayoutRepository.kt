package io.github.anderescaray.cents.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.anderescaray.cents.domain.model.BubblePlacement
import io.github.anderescaray.cents.domain.model.HomeLayout
import io.github.anderescaray.cents.domain.repository.HomeLayoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The home layout in DataStore: three floats per bubble, a flag and a name.
 *
 * Flat keys rather than one serialised blob, because the three numbers are written one gesture
 * at a time and a blob would make every drag a read-modify-write of everything. A key that has
 * never been written is simply absent, which is what lets [HomeLayout] hold only what was
 * actually moved.
 */
@Singleton
class DataStoreHomeLayoutRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : HomeLayoutRepository {
        override fun observe(): Flow<HomeLayout> =
            dataStore.data.map { preferences ->
                HomeLayout(
                    placements = preferences.placements(),
                    tabBarCollapsed = preferences[TAB_BAR_COLLAPSED] == true,
                    lastSection = preferences[LAST_SECTION],
                )
            }

        override suspend fun place(
            section: String,
            placement: BubblePlacement,
        ) {
            dataStore.edit { preferences ->
                preferences[centerXKey(section)] = placement.centerX
                preferences[centerYKey(section)] = placement.centerY
                preferences[diameterKey(section)] = placement.diameter
            }
        }

        override suspend fun setTabBarCollapsed(collapsed: Boolean) {
            dataStore.edit { it[TAB_BAR_COLLAPSED] = collapsed }
        }

        override suspend fun setLastSection(section: String?) {
            dataStore.edit { preferences ->
                if (section == null) preferences.remove(LAST_SECTION) else preferences[LAST_SECTION] = section
            }
        }

        override suspend fun resetPlacements() {
            dataStore.edit { preferences ->
                preferences
                    .asMap()
                    .keys
                    .filter { it.name.startsWith(BUBBLE_PREFIX) }
                    .forEach { preferences.remove(it) }
            }
        }

        /**
         * A bubble counts as placed only when all three of its numbers are there. A half-written
         * bubble — one key present, the others not — is treated as unplaced rather than as a
         * position with invented parts, which is the shape a future key rename would leave.
         */
        private fun Preferences.placements(): Map<String, BubblePlacement> =
            asMap()
                .keys
                .mapNotNull { key -> key.name.sectionOf(CENTER_X_SUFFIX) }
                .mapNotNull { section ->
                    val x = this[centerXKey(section)]
                    val y = this[centerYKey(section)]
                    val diameter = this[diameterKey(section)]
                    if (x == null || y == null || diameter == null) {
                        null
                    } else {
                        section to BubblePlacement(x, y, diameter)
                    }
                }.toMap()

        private fun String.sectionOf(suffix: String): String? =
            takeIf { it.startsWith(BUBBLE_PREFIX) && it.endsWith(suffix) }
                ?.removePrefix(BUBBLE_PREFIX)
                ?.removeSuffix(suffix)

        private companion object {
            const val BUBBLE_PREFIX = "bubble."
            const val CENTER_X_SUFFIX = ".x"
            const val CENTER_Y_SUFFIX = ".y"
            const val DIAMETER_SUFFIX = ".d"

            val TAB_BAR_COLLAPSED = booleanPreferencesKey("tabBarCollapsed")
            val LAST_SECTION = stringPreferencesKey("lastSection")

            fun centerXKey(section: String) = floatPreferencesKey("$BUBBLE_PREFIX$section$CENTER_X_SUFFIX")

            fun centerYKey(section: String) = floatPreferencesKey("$BUBBLE_PREFIX$section$CENTER_Y_SUFFIX")

            fun diameterKey(section: String) = floatPreferencesKey("$BUBBLE_PREFIX$section$DIAMETER_SUFFIX")
        }
    }

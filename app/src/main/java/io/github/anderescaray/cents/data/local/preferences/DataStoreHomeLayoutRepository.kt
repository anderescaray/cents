package io.github.anderescaray.cents.data.local.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.anderescaray.cents.domain.model.BubblePlacement
import io.github.anderescaray.cents.domain.model.HomeLayout
import io.github.anderescaray.cents.domain.repository.HomeLayoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The home layout in DataStore: three floats per bubble, a flag and a name.
 *
 * Flat keys rather than one serialised blob, because the three numbers are written one gesture
 * at a time and a blob would make every drag a read-modify-write of everything. A key that has
 * never been written is simply absent, which is what lets [HomeLayout] hold only what was
 * actually moved.
 *
 * **Nothing here throws**, and that is a decision rather than defensiveness. The canvas is the
 * first screen now, so a store that cannot be read would not break a corner of the app: it would
 * be a blank screen and a crash on every launch, with no way in. A read that fails reads as a
 * canvas nobody has arranged, and a write that fails leaves the bubble where it was — in both
 * cases the worst outcome is losing *the arrangement*, which is all this file holds.
 */
@Singleton
class DataStoreHomeLayoutRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : HomeLayoutRepository {
        override fun observe(): Flow<HomeLayout> =
            dataStore.data
                .catch { failure ->
                    // Only an I/O failure reads as an empty canvas: anything else is a bug, and
                    // swallowing it here would swallow it everywhere.
                    if (failure is IOException) emit(emptyPreferences()) else throw failure
                }.map { preferences ->
                    HomeLayout(
                        placements = preferences.placements(),
                        tabBarCollapsed = preferences[TAB_BAR_COLLAPSED] == true,
                        lastSection = preferences[LAST_SECTION],
                    )
                }

        override suspend fun place(
            section: String,
            placement: BubblePlacement,
        ) = write { preferences ->
            preferences[centerXKey(section)] = placement.centerX
            preferences[centerYKey(section)] = placement.centerY
            preferences[diameterKey(section)] = placement.diameter
        }

        override suspend fun setTabBarCollapsed(collapsed: Boolean) = write { it[TAB_BAR_COLLAPSED] = collapsed }

        override suspend fun setLastSection(section: String?) =
            write { preferences ->
                if (section == null) preferences.remove(LAST_SECTION) else preferences[LAST_SECTION] = section
            }

        override suspend fun resetPlacements() =
            write { preferences ->
                preferences
                    .asMap()
                    .keys
                    .filter { it.name.startsWith(BUBBLE_PREFIX) }
                    .forEach { preferences.remove(it) }
            }

        /**
         * A write that cannot reach the disk — a full one, most likely — is dropped rather than
         * thrown. These are called from a view model scope, where an escaping exception cancels
         * the scope and takes the app with it, and what is lost by dropping one is where a
         * bubble sits.
         */
        private suspend fun write(change: (MutablePreferences) -> Unit) {
            try {
                dataStore.edit(change)
            } catch (failure: IOException) {
                Log.w(TAG, "the home layout could not be saved", failure)
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
            const val TAG = "HomeLayout"
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

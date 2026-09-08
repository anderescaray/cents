package io.github.anderescaray.cents.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import io.github.anderescaray.cents.domain.model.BubblePlacement
import io.github.anderescaray.cents.domain.model.HomeLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

/**
 * The canvas as the owner left it, across a restart. Written against a real DataStore on a real
 * file rather than a fake, because what is being checked is the storage: the keys, what a
 * missing one means, and what reset does and does not take with it.
 *
 * Robolectric because a dropped write is logged, and `android.util.Log` is not a real class on a
 * bare JVM — the store itself needs nothing from Android.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreHomeLayoutRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a placement comes back as it was left`() =
        runTest {
            val repository = DataStoreHomeLayoutRepository(store())
            val placement = BubblePlacement(centerX = 0.21f, centerY = 0.62f, diameter = 0.33f)

            repository.place("ACCOUNTS", placement)

            assertEquals(placement, repository.observe().first().placements["ACCOUNTS"])
        }

    @Test
    fun `a section nobody moved is simply absent, not a position of zero`() =
        runTest {
            val repository = DataStoreHomeLayoutRepository(store())

            repository.place("ACCOUNTS", BubblePlacement(0.5f, 0.5f, 0.3f))

            val layout = repository.observe().first()
            assertEquals(setOf("ACCOUNTS"), layout.placements.keys)
        }

    @Test
    fun `the tab bar and the last section survive a reset of the bubbles`() =
        runTest {
            val repository = DataStoreHomeLayoutRepository(store())
            repository.place("REPORTS", BubblePlacement(0.4f, 0.4f, 0.4f))
            repository.setTabBarCollapsed(true)
            repository.setLastSection("REPORTS")

            repository.resetPlacements()

            val layout = repository.observe().first()
            assertTrue("the bubbles go back to their defaults", layout.placements.isEmpty())
            assertTrue("collapsing the bar is not part of the layout", layout.tabBarCollapsed)
            assertEquals("REPORTS", layout.lastSection)
        }

    @Test
    fun `no last section means the canvas, not a section named null`() =
        runTest {
            val repository = DataStoreHomeLayoutRepository(store())
            repository.setLastSection("SETTINGS")

            repository.setLastSection(null)

            assertNull(repository.observe().first().lastSection)
        }

    @Test
    fun `a half written bubble reads as one nobody moved`() =
        runTest {
            val store = store()
            val repository = DataStoreHomeLayoutRepository(store)
            // The shape a renamed or partly migrated key would leave behind: an x with no y.
            store.edit { it[floatPreferencesKey("bubble.ACCOUNTS.x")] = 0.3f }

            assertTrue(
                repository
                    .observe()
                    .first()
                    .placements
                    .isEmpty(),
            )
        }

    @Test
    fun `a store that cannot be read is a canvas nobody has arranged, not a crash`() =
        runTest {
            val file = File(folder.newFolder(), "cents.preferences_pb")
            file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            val repository = DataStoreHomeLayoutRepository(storeOn(file))

            // The canvas is the first screen, so this used to be a blank screen and a crash on
            // every launch, with no way into the app at all.
            assertEquals(HomeLayout.Untouched, repository.observe().first())
        }

    @Test
    fun `a write that cannot reach the disk leaves the bubble where it was`() =
        runTest {
            val repository = DataStoreHomeLayoutRepository(UnwritableStore)

            // It is called from a view model scope, where an escaping exception cancels the
            // scope and takes the app with it. What is dropped instead is one bubble position.
            repository.place("ACCOUNTS", BubblePlacement(0.5f, 0.5f, 0.3f))
            repository.setTabBarCollapsed(true)
            repository.setLastSection("ACCOUNTS")
            repository.resetPlacements()
        }

    /** A store per test, on its own file: two DataStores over one file is an error by design. */
    private fun TestScope.store(): DataStore<Preferences> = storeOn(File(folder.newFolder(), "cents.preferences_pb"))

    private fun TestScope.storeOn(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(coroutineContext + Job()),
            produceFile = { file },
        )
}

/** A disk that says no, to whatever is asked of it. */
private object UnwritableStore : DataStore<Preferences> {
    override val data = flow<Preferences> { throw IOException("no space left on device") }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        throw IOException("no space left on device")
}

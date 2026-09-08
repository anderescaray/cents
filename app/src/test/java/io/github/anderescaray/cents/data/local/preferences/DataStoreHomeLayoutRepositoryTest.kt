package io.github.anderescaray.cents.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import io.github.anderescaray.cents.domain.model.BubblePlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The canvas as the owner left it, across a restart. Written against a real DataStore on a real
 * file rather than a fake, because what is being checked is the storage: the keys, what a
 * missing one means, and what reset does and does not take with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    /** A store per test, on its own file: two DataStores over one file is an error by design. */
    private fun TestScope.store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(coroutineContext + Job()),
            produceFile = { File(folder.newFolder(), "cents.preferences_pb") },
        )
}

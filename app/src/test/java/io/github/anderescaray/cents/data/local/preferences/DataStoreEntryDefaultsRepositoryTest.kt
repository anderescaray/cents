package io.github.anderescaray.cents.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The movement form's remembered account, on a real DataStore file (`docs/next-improvements.md` §5). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreEntryDefaultsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `nothing is remembered before the first movement`() =
        runTest {
            assertNull(DataStoreEntryDefaultsRepository(store(file()), backgroundScope).observeLastAccountId().first())
        }

    @Test
    fun `the newest account remembered is the one read back`() =
        runTest {
            val repository = DataStoreEntryDefaultsRepository(store(file()), backgroundScope)

            // Handed off and returned from at once; the write lands in its own time.
            repository.rememberLastAccount(3)
            repository.rememberLastAccount(7)

            assertEquals(7L, repository.observeLastAccountId().first { it == 7L })
        }

    private fun file(): File = File(folder.newFolder(), "cents.preferences_pb")

    private fun TestScope.store(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(coroutineContext + Job()),
            produceFile = { file },
        )
}

package io.github.anderescaray.cents.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.anderescaray.cents.domain.model.MoneyFigure
import io.github.anderescaray.cents.domain.model.MoneyFigures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Which money figures Home shows, on a real DataStore file (`DECISIONS.md` D34). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreMoneyFiguresRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `before any choice, the two parts show and the total does not`() =
        runTest {
            val figures = DataStoreMoneyFiguresRepository(store(file())).observe().first()

            assertEquals(MoneyFigures(normal = true, invested = true, total = false), figures)
        }

    @Test
    fun `each switch is kept by itself, and none at all is a choice too`() =
        runTest {
            val repository = DataStoreMoneyFiguresRepository(store(file()))

            repository.set(MoneyFigure.TOTAL, on = true)
            repository.set(MoneyFigure.INVESTED, on = false)
            assertEquals(MoneyFigures(normal = true, invested = false, total = true), repository.observe().first())

            repository.set(MoneyFigure.NORMAL, on = false)
            repository.set(MoneyFigure.TOTAL, on = false)
            assertEquals(MoneyFigures(normal = false, invested = false, total = false), repository.observe().first())
        }

    private fun file(): File = File(folder.newFolder(), "cents.preferences_pb")

    private fun TestScope.store(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(coroutineContext + Job()),
            produceFile = { file },
        )
}

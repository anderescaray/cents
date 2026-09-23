package io.github.anderescaray.cents.ui.feature.settings

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.data.export.BackupWriter
import io.github.anderescaray.cents.ui.feature.ViewModelKeeper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RestoreReadingSafetyTest {
    private val viewModels = ViewModelKeeper()
    private val repository = SettingsBackupRepository()
    private lateinit var reader: SettingsDocumentReader
    private lateinit var viewModel: RestoreViewModel
    private val file = Uri.parse("content://backup/chosen.json")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        reader = SettingsDocumentReader(ApplicationProvider.getApplicationContext())
        reader.text = BackupWriter.toJson(backupWithAccounts("Savings", "Broker"))
        viewModel = viewModels.keep(RestoreViewModel(repository, reader))
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
    fun `cancelling discards the pending replacement`() =
        runTest {
            viewModel.readFrom(file)
            viewModel.uiState.first { it.step == RestoreStep.CONFIRMING }
            viewModel.cancel()
            viewModel.confirm()
            advanceUntilIdle()
            assertEquals(0, repository.restores)
            assertEquals(RestoreStep.IDLE, viewModel.uiState.value.step)
        }

    @Test
    fun `a cancelled read cannot reopen the confirmation`() =
        runTest {
            val pending = CompletableDeferred<String>()
            reader.waiting = pending
            viewModel.readFrom(file)
            viewModel.cancel()
            pending.complete(reader.text)
            advanceUntilIdle()
            assertEquals(RestoreStep.IDLE, viewModel.uiState.value.step)
        }

    @Test
    fun `a failed current snapshot cannot offer a confirmation missing what will be lost`() =
        runTest {
            repository.failSnapshot = true
            viewModel.readFrom(file)
            val state = viewModel.uiState.first { it.step != RestoreStep.READING }
            // Its own step, not FAILED, whose message says a write failed when nothing was written.
            assertEquals(RestoreStep.CURRENT_UNREADABLE, state.step)
            assertEquals(0, repository.restores)
            viewModel.confirm()
            advanceUntilIdle()
            assertEquals(0, repository.restores)
        }

    @Test
    fun `a repeated confirm cannot replace the ledger twice`() =
        runTest {
            viewModel.readFrom(file)
            viewModel.uiState.first { it.step == RestoreStep.CONFIRMING }
            viewModel.confirm()
            viewModel.confirm()
            advanceUntilIdle()
            assertEquals(1, repository.restores)
        }
}

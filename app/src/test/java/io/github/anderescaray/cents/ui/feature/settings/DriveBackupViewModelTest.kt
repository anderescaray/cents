package io.github.anderescaray.cents.ui.feature.settings

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.model.DriveBackupException
import io.github.anderescaray.cents.domain.model.DriveBackupFailure
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.ui.backup.DriveBackupSession
import io.github.anderescaray.cents.ui.backup.DriveGrant
import io.github.anderescaray.cents.ui.backup.FakeDriveAuthorization
import io.github.anderescaray.cents.ui.backup.FakeDriveBackupScheduler
import io.github.anderescaray.cents.ui.backup.FakeDriveBackupStorage
import io.github.anderescaray.cents.ui.backup.MemoryDriveBackupRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DriveBackupViewModelTest {
    private val viewModels = ViewModelKeeper()
    private val preferences = MemoryDriveBackupRepository()
    private val authorization = FakeDriveAuthorization()
    private val storage = FakeDriveBackupStorage()
    private val scheduler = FakeDriveBackupScheduler()
    private lateinit var viewModel: DriveBackupViewModel
    private lateinit var context: Context
    private val backup = DriveBackup("chosen", "cents-backup-2026-09-22T031200.json", backupTestClock.instant(), 2048)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        storage.files = listOf(backup)
        viewModel =
            viewModels.keep(
                DriveBackupViewModel(
                    preferences,
                    DriveBackupSession(authorization, storage, preferences, backupTestClock),
                    scheduler,
                ),
            )
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
    fun `enabling without a grant requests a resolution before saving or scheduling`() =
        runTest {
            authorization.grant = resolution()
            viewModel.uiState.test {
                assertTrue(awaitItem().loaded)
                viewModel.setAutomaticEnabled(true)
                val waiting = expectMostRecentItem()
                assertEquals(DriveBackupStep.WAITING_FOR_AUTHORIZATION, waiting.step)
                assertNotNull(waiting.resolution)
                assertFalse(preferences.state.value.automaticEnabled)
                assertTrue(scheduler.changes.isEmpty())

                viewModel.resolutionLaunched()
                assertNull(expectMostRecentItem().resolution)
                viewModel.authorizationResult(Activity.RESULT_OK, Intent())
                val enabled = expectMostRecentItem()
                assertEquals(DriveBackupStep.IDLE, enabled.step)
                assertTrue(enabled.preferences.automaticEnabled)
                assertEquals(listOf(true), scheduler.changes)
                assertEquals(0, storage.uploads)
            }
        }

    @Test
    fun `cancelling consent leaves automatic backup off without inventing a failure`() =
        runTest {
            authorization.grant = resolution()
            viewModel.setAutomaticEnabled(true)
            viewModel.authorizationResult(Activity.RESULT_CANCELED, null)
            assertFalse(preferences.state.value.automaticEnabled)
            assertTrue(scheduler.changes.isEmpty())
            assertNull(preferences.state.value.lastFailure)
            assertEquals(DriveBackupMessage.AUTHORIZATION_CANCELLED, viewModel.uiState.value.message)
        }

    @Test
    fun `a failed intent sender is a failure not consent cancellation`() =
        runTest {
            authorization.grant = resolution()
            viewModel.setAutomaticEnabled(true)
            val result = Intent().putExtra(StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION, true)
            viewModel.authorizationResult(Activity.RESULT_CANCELED, result)
            assertEquals(
                DriveBackupFailureKind.OTHER,
                preferences.state.value.lastFailure
                    ?.kind,
            )
            assertFalse(preferences.state.value.automaticEnabled)
            assertTrue(scheduler.changes.isEmpty())
            assertNull(viewModel.uiState.value.message)
        }

    @Test
    fun `a failed schedule cannot leave the switch claiming backups are automatic`() =
        runTest {
            scheduler.failure = IllegalStateException("scheduling failed")
            viewModel.setAutomaticEnabled(true)
            assertFalse(preferences.state.value.automaticEnabled)
            assertEquals(
                DriveBackupFailureKind.OTHER,
                preferences.state.value.lastFailure
                    ?.kind,
            )
            assertTrue(scheduler.changes.isEmpty())
        }

    @Test
    fun `turning the toggle off cancels work without authorizing again`() =
        runTest {
            preferences.setAutomaticEnabled(true)
            viewModel.setAutomaticEnabled(false)
            assertEquals(listOf(false), scheduler.changes)
            assertFalse(preferences.state.value.automaticEnabled)
            assertEquals(0, authorization.requests)
        }

    @Test
    fun `reconnecting clears the warning but neither schedules work nor claims a backup`() =
        runTest {
            preferences.setAutomaticEnabled(true)
            preferences.recordFailure(
                DriveBackupFailure(backupTestClock.instant(), DriveBackupFailureKind.NEEDS_RECONNECTING),
            )
            viewModel.reconnect()
            assertNull(preferences.state.value.lastFailure)
            assertNull(preferences.state.value.lastSuccessAt)
            assertTrue(scheduler.changes.isEmpty())
            assertTrue(preferences.state.value.automaticEnabled)
            assertEquals(DriveBackupMessage.CONNECTED, viewModel.uiState.value.message)
        }

    @Test
    fun `each manual backup asks Play services for a token instead of keeping one`() =
        runTest {
            authorization.grant = DriveGrant.Authorized("first-token")
            viewModel.backUpNow()
            authorization.grant = DriveGrant.Authorized("second-token")
            viewModel.backUpNow()
            assertEquals(2, authorization.requests)
            assertEquals(listOf("first-token", "second-token"), storage.tokens)
            assertEquals(backupTestClock.instant(), preferences.state.value.lastSuccessAt)
            assertEquals(DriveBackupMessage.BACKED_UP, viewModel.uiState.value.message)
            assertTrue(scheduler.changes.isEmpty())
        }

    @Test
    fun `another tap while uploading does not start a second run`() =
        runTest {
            storage.waiting = CompletableDeferred()
            viewModel.backUpNow()
            assertEquals(DriveBackupStep.BACKING_UP, viewModel.uiState.value.step)
            viewModel.backUpNow()
            assertEquals(1, authorization.requests)
            storage.waiting!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, storage.uploads)
        }

    @Test
    fun `a grant refused even with a fresh token is recorded and exposes reconnecting`() =
        runTest {
            storage.failure = DriveBackupException(DriveBackupFailureKind.NEEDS_RECONNECTING)
            viewModel.backUpNow()
            assertEquals(
                DriveBackupFailureKind.NEEDS_RECONNECTING,
                viewModel.uiState.value.preferences.lastFailure
                    ?.kind,
            )
            assertEquals(
                backupTestClock.instant(),
                preferences.state.value.lastFailure
                    ?.at,
            )
            assertEquals(listOf("test-access-token", "test-access-token"), authorization.invalidated)
            assertNull(preferences.state.value.lastSuccessAt)
        }

    @Test
    fun `a too new Drive copy has exactly the same refusal as the file picker`() =
        runTest { assertSameRefusal("""{"schemaVersion":99,"accounts":[]}""", RestoreProblem.TOO_NEW) }

    @Test
    fun `a damaged Drive copy has exactly the same refusal as the file picker`() =
        runTest {
            assertSameRefusal("""{"schemaVersion":6,"accounts":[{"id":1}]}""", RestoreProblem.DAMAGED)
        }

    @Test
    fun `closing a downloading list cannot deliver a late file into restore`() =
        runTest {
            storage.waiting = CompletableDeferred()
            viewModel.showBackups()
            var delivered = false
            viewModel.chooseBackup(backup) { delivered = true }
            assertEquals(DriveBackupStep.DOWNLOADING, viewModel.uiState.value.step)
            viewModel.closeBackups()
            storage.waiting!!.complete(Unit)
            advanceUntilIdle()
            assertFalse(delivered)
            assertFalse(viewModel.uiState.value.showBackups)
            assertNull(preferences.state.value.lastFailure)
        }

    private suspend fun assertSameRefusal(
        text: String,
        problem: RestoreProblem,
    ) {
        storage.contents = text
        val repository = SettingsBackupRepository()
        val reader = SettingsDocumentReader(context).also { it.text = text }
        val fromFile = viewModels.keep(RestoreViewModel(repository, reader))
        val fromDrive = viewModels.keep(RestoreViewModel(repository, reader))
        fromFile.readFrom(Uri.parse("content://backup/file.json"))
        viewModel.showBackups()
        viewModel.chooseBackup(backup, fromDrive::readFromDrive)
        val fileState = fromFile.uiState.first { it.step == RestoreStep.REFUSED }
        val driveState = fromDrive.uiState.first { it.step == RestoreStep.REFUSED }
        assertEquals(problem, driveState.problem)
        assertEquals(fileState, driveState)
        assertEquals(0, repository.restores)
        assertEquals(2, authorization.requests)
        assertFalse(viewModel.uiState.value.showBackups)
    }

    private fun resolution(): DriveGrant.Resolution =
        DriveGrant.Resolution(PendingIntent.getActivity(context, 0, Intent("authorize"), PendingIntent.FLAG_IMMUTABLE))
}

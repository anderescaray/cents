package io.github.anderescaray.cents.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.preferences.DataStoreDriveBackupRepository
import io.github.anderescaray.cents.domain.model.DriveBackupFailure
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.domain.model.DriveBackupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class DataStoreDriveBackupRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val now = Instant.parse("2026-09-22T03:12:00Z")

    @Test
    fun `a fresh install has no automatic upload or invented backup time`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file() }
            assertEquals(DriveBackupState(), DataStoreDriveBackupRepository(store).observe().first())
        }

    @Test
    fun `status changes flow together and success clears the last failure`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file() }
            val repository = DataStoreDriveBackupRepository(store)
            val failure = DriveBackupFailure(now, DriveBackupFailureKind.NETWORK)

            repository.observe().test {
                assertEquals(DriveBackupState(), awaitItem())
                repository.setAutomaticEnabled(true)
                assertTrue(awaitItem().automaticEnabled)
                repository.recordFailure(failure)
                assertEquals(failure, awaitItem().lastFailure)
                repository.recordSuccess(now.plusSeconds(60))
                val success = awaitItem()
                assertEquals(now.plusSeconds(60), success.lastSuccessAt)
                assertNull(success.lastFailure)
                assertTrue(success.automaticEnabled)
            }
        }

    @Test
    fun `all status fields survive reopening the store and turning the toggle off`() =
        runTest {
            val file = file()
            val job = Job(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + job)
            val first = DataStoreDriveBackupRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            first.setAutomaticEnabled(true)
            first.recordSuccess(now)
            val failure = DriveBackupFailure(now.plusSeconds(60), DriveBackupFailureKind.NEEDS_RECONNECTING)
            first.recordFailure(failure)
            job.cancelAndJoin()

            val second =
                DataStoreDriveBackupRepository(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { file },
                )
            assertEquals(DriveBackupState(true, now, failure), second.observe().first())
            second.setAutomaticEnabled(false)
            val disabled = second.observe().first()
            assertFalse(disabled.automaticEnabled)
            assertEquals(now, disabled.lastSuccessAt)
            assertEquals(failure, disabled.lastFailure)
        }

    @Test
    fun `reconnecting clears only an authorization failure and does not claim a backup`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file() }
            val repository = DataStoreDriveBackupRepository(store)
            repository.recordFailure(DriveBackupFailure(now, DriveBackupFailureKind.NEEDS_RECONNECTING))
            repository.clearReconnectRequired()
            assertEquals(DriveBackupState(), repository.observe().first())

            val failure = DriveBackupFailure(now, DriveBackupFailureKind.OTHER)
            repository.recordFailure(failure)
            repository.clearReconnectRequired()
            assertEquals(failure, repository.observe().first().lastFailure)
        }

    private fun file() = File(folder.newFolder(), "backup.preferences_pb")
}

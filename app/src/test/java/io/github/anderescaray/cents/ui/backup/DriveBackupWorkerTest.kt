package io.github.anderescaray.cents.ui.backup

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import io.github.anderescaray.cents.domain.model.DriveBackupException
import io.github.anderescaray.cents.domain.model.DriveBackupFailure
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.domain.model.DriveBackupState
import io.github.anderescaray.cents.domain.repository.DriveBackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(application = BackupWorkerTestApplication::class)
class DriveBackupWorkerTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-22T03:12:00Z"), ZoneOffset.UTC)
    private val preferences = MemoryDriveBackupRepository(DriveBackupState(automaticEnabled = true))
    private val authorization = FakeDriveAuthorization()
    private val storage = FakeDriveBackupStorage()
    private lateinit var application: BackupWorkerTestApplication
    private lateinit var worker: DriveBackupWorker

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        val session = DriveBackupSession(authorization, storage, preferences, clock)
        application.dependencies =
            object : DriveBackupWorker.Dependencies, GeneratedComponent {
                override fun preferences(): DriveBackupRepository = preferences

                override fun session(): DriveBackupSession = session
            }
        worker = TestListenableWorkerBuilder.from(application, DriveBackupWorker::class.java).build()
    }

    @Test
    fun `a successful run records its time and clears a previous failure`() =
        runTest {
            preferences.recordFailure(DriveBackupFailure(Instant.EPOCH, DriveBackupFailureKind.NETWORK))

            assertEquals(Result.success(), worker.doWork())

            assertEquals(clock.instant(), preferences.state.value.lastSuccessAt)
            assertNull(preferences.state.value.lastFailure)
            assertEquals(1, authorization.requests)
            assertEquals(1, storage.uploads)
        }

    @Test
    fun `a resolution is recorded as needs reconnecting and returns failure rather than retry`() =
        runTest {
            authorization.grant =
                DriveGrant.Resolution(
                    PendingIntent.getActivity(application, 0, Intent("authorize"), PendingIntent.FLAG_IMMUTABLE),
                )

            assertEquals(Result.failure(), worker.doWork())

            assertEquals(
                DriveBackupFailure(clock.instant(), DriveBackupFailureKind.NEEDS_RECONNECTING),
                preferences.state.value.lastFailure,
            )
            assertEquals(0, storage.uploads)
            assertNull(preferences.state.value.lastSuccessAt)
            assertEquals(Result.failure(), worker.doWork())
            assertEquals(1, authorization.requests)
        }

    @Test
    fun `reconnecting lets the existing daily job resume without a new schedule`() =
        runTest {
            preferences.recordFailure(DriveBackupFailure(clock.instant(), DriveBackupFailureKind.NEEDS_RECONNECTING))
            assertEquals(Result.failure(), worker.doWork())
            preferences.clearReconnectRequired()
            authorization.grant = DriveGrant.Authorized("renewed-token")

            assertEquals(Result.success(), worker.doWork())
            assertEquals(listOf("renewed-token"), storage.tokens)
            assertEquals(1, authorization.requests)
            assertNull(preferences.state.value.lastFailure)
        }

    @Test
    fun `one rejected token is renewed silently and the backup still happens`() =
        runTest {
            storage.failures += DriveBackupException(DriveBackupFailureKind.NEEDS_RECONNECTING)
            authorization.grants += DriveGrant.Authorized("stale-token")

            assertEquals(Result.success(), worker.doWork())

            assertEquals(listOf("stale-token"), authorization.invalidated)
            assertEquals(listOf("stale-token", "test-access-token"), storage.tokens)
            assertEquals(1, storage.uploads)
            assertNull(preferences.state.value.lastFailure)
            assertEquals(clock.instant(), preferences.state.value.lastSuccessAt)
        }

    @Test
    fun `a rejected token needs reconnecting only when the silent renewal needs the owner`() =
        runTest {
            storage.failures += DriveBackupException(DriveBackupFailureKind.NEEDS_RECONNECTING)
            authorization.grants += DriveGrant.Authorized("stale-token")
            authorization.grants +=
                DriveGrant.Resolution(
                    PendingIntent.getActivity(application, 0, Intent("authorize"), PendingIntent.FLAG_IMMUTABLE),
                )

            assertEquals(Result.failure(), worker.doWork())

            assertEquals(listOf("stale-token"), authorization.invalidated)
            assertEquals(0, storage.uploads)
            assertEquals(
                DriveBackupFailureKind.NEEDS_RECONNECTING,
                preferences.state.value.lastFailure
                    ?.kind,
            )
        }

    @Test
    fun `a token refused again after renewal requires reconnecting`() =
        runTest {
            storage.failure = DriveBackupException(DriveBackupFailureKind.NEEDS_RECONNECTING)

            assertEquals(Result.failure(), worker.doWork())

            assertEquals(2, authorization.requests)
            assertEquals(listOf("test-access-token", "test-access-token"), authorization.invalidated)
            assertEquals(
                DriveBackupFailureKind.NEEDS_RECONNECTING,
                preferences.state.value.lastFailure
                    ?.kind,
            )
        }

    @Test
    fun `a full Drive is recorded as such and not retried in a loop`() =
        runTest {
            storage.failure = DriveBackupException(DriveBackupFailureKind.DRIVE_FULL)

            assertEquals(Result.failure(), worker.doWork())

            assertEquals(
                DriveBackupFailure(clock.instant(), DriveBackupFailureKind.DRIVE_FULL),
                preferences.state.value.lastFailure,
            )
            assertEquals(1, authorization.requests)
        }

    @Test
    fun `a network failure records when it happened and retries`() =
        runTest {
            storage.failure = DriveBackupException(DriveBackupFailureKind.NETWORK)

            assertEquals(Result.retry(), worker.doWork())

            assertEquals(
                DriveBackupFailure(clock.instant(), DriveBackupFailureKind.NETWORK),
                preferences.state.value.lastFailure,
            )
            assertNull(preferences.state.value.lastSuccessAt)
        }

    @Test
    fun `a local IO failure is not mislabeled as a network problem`() =
        runTest {
            storage.failure = IOException("snapshot unavailable")
            assertEquals(Result.retry(), worker.doWork())
            assertEquals(
                DriveBackupFailureKind.OTHER,
                preferences.state.value.lastFailure
                    ?.kind,
            )
        }

    @Test
    fun `other failures are recorded and retried too`() =
        runTest {
            authorization.failure = IllegalStateException("Play services unavailable")
            assertEquals(Result.retry(), worker.doWork())
            assertEquals(
                DriveBackupFailure(clock.instant(), DriveBackupFailureKind.OTHER),
                preferences.state.value.lastFailure,
            )
        }

    @Test
    fun `a disabled toggle makes even a queued worker harmless`() =
        runTest {
            preferences.setAutomaticEnabled(false)
            assertEquals(Result.success(), worker.doWork())
            assertEquals(0, authorization.requests)
            assertEquals(0, storage.uploads)
        }

    @Test
    fun `cancellation is neither a failed backup nor a retry`() =
        runTest {
            storage.failure = CancellationException("work cancelled")
            val failure = runCatching { worker.doWork() }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertNull(preferences.state.value.lastFailure)
            assertNull(preferences.state.value.lastSuccessAt)
        }
}

class BackupWorkerTestApplication :
    Application(),
    GeneratedComponentManager<Any> {
    lateinit var dependencies: DriveBackupWorker.Dependencies

    override fun generatedComponent(): Any = dependencies
}

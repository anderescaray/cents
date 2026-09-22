package io.github.anderescaray.cents.ui.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class DriveBackupSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `the job is daily network constrained and backs off exponentially`() {
        val spec = WorkManagerDriveBackupScheduler.request().workSpec
        assertEquals(TimeUnit.DAYS.toMillis(1), spec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(15), spec.backoffDelayDuration)
    }

    @Test
    fun `enabling twice keeps one unique job and disabling cancels it`() =
        runTest {
            val scheduler = WorkManagerDriveBackupScheduler(context)
            val manager = WorkManager.getInstance(context)
            scheduler.setEnabled(true)
            val first = manager.getWorkInfosForUniqueWork(WorkManagerDriveBackupScheduler.UNIQUE_NAME).get().single()
            scheduler.setEnabled(true)
            val repeated = manager.getWorkInfosForUniqueWork(WorkManagerDriveBackupScheduler.UNIQUE_NAME).get()
            assertEquals(listOf(first.id), repeated.map { it.id })
            assertTrue(repeated.none { it.state.isFinished })

            scheduler.setEnabled(false)
            val cancelled =
                manager
                    .getWorkInfosForUniqueWork(
                        WorkManagerDriveBackupScheduler.UNIQUE_NAME,
                    ).get()
                    .single()
            assertEquals(WorkInfo.State.CANCELLED, cancelled.state)
        }
}

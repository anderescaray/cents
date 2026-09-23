package io.github.anderescaray.cents.ui.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface DriveBackupScheduler {
    suspend fun setEnabled(enabled: Boolean)
}

@Singleton
class WorkManagerDriveBackupScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DriveBackupScheduler {
        override suspend fun setEnabled(enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            val operation =
                if (enabled) {
                    manager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request())
                } else {
                    manager.cancelUniqueWork(UNIQUE_NAME)
                }
            operation.await()
        }

        internal companion object {
            const val UNIQUE_NAME = "drive-backup"
            private const val BACKOFF_MINUTES = 15L

            fun request(): PeriodicWorkRequest =
                PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .build()
        }
    }

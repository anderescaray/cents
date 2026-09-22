package io.github.anderescaray.cents.ui.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.domain.model.DriveBackupException
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.domain.repository.DriveBackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class DriveBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun preferences(): DriveBackupRepository

        fun session(): DriveBackupSession
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val session = dependencies.session()
        return try {
            val state = dependencies.preferences().observe().first()
            when {
                !state.automaticEnabled -> {
                    Result.success()
                }

                state.lastFailure?.kind == DriveBackupFailureKind.NEEDS_RECONNECTING -> {
                    Result.failure()
                }

                else -> {
                    val grant = session.authorize()
                    if (grant !is DriveGrant.Authorized) {
                        throw DriveBackupException(DriveBackupFailureKind.NEEDS_RECONNECTING)
                    }
                    session.backUp(grant.accessToken)
                    Result.success()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            recordFailure(session, failure)
        }
    }

    private suspend fun recordFailure(
        session: DriveBackupSession,
        failure: Exception,
    ): Result =
        try {
            val recorded = session.recordFailure(failure)
            if (recorded.kind == DriveBackupFailureKind.NEEDS_RECONNECTING) Result.failure() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
}

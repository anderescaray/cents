package io.github.anderescaray.cents.ui.backup

import android.content.Intent
import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.model.DriveBackupException
import io.github.anderescaray.cents.domain.model.DriveBackupFailure
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.domain.repository.DriveBackupRepository
import io.github.anderescaray.cents.domain.repository.DriveBackupStorage
import kotlinx.coroutines.CancellationException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveBackupSession
    @Inject
    constructor(
        private val authorization: DriveAuthorization,
        private val storage: DriveBackupStorage,
        private val preferences: DriveBackupRepository,
        private val clock: Clock,
    ) {
        suspend fun authorize(): DriveGrant = authorization.authorize()

        fun fromIntent(intent: Intent): DriveGrant = authorization.fromIntent(intent)

        suspend fun backUp(accessToken: String) {
            withToken(accessToken) {
                storage.create(accessToken)
                preferences.recordSuccess(clock.instant())
            }
        }

        suspend fun list(accessToken: String): List<DriveBackup> = withToken(accessToken) { storage.list(accessToken) }

        suspend fun download(
            accessToken: String,
            id: String,
        ): String = withToken(accessToken) { storage.download(accessToken, id) }

        suspend fun recordFailure(failure: Exception): DriveBackupFailure {
            if (failure is CancellationException) throw failure
            val record = DriveBackupFailure(clock.instant(), failure.backupFailureKind())
            preferences.recordFailure(record)
            return record
        }

        private suspend fun <T> withToken(
            accessToken: String,
            action: suspend () -> T,
        ): T =
            try {
                action()
            } catch (failure: DriveBackupException) {
                if (failure.kind == DriveBackupFailureKind.NEEDS_RECONNECTING) {
                    try {
                        authorization.invalidateToken(accessToken)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Unit
                    }
                }
                throw failure
            }
    }

internal fun Exception.backupFailureKind(): DriveBackupFailureKind =
    (this as? DriveBackupException)?.kind ?: DriveBackupFailureKind.OTHER

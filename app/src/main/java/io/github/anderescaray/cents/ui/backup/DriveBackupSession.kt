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
            withToken(accessToken) { token -> storage.create(token) }
            preferences.recordSuccess(clock.instant())
        }

        suspend fun list(accessToken: String): List<DriveBackup> = withToken(accessToken) { storage.list(it) }

        suspend fun download(
            accessToken: String,
            id: String,
        ): String = withToken(accessToken) { storage.download(it, id) }

        suspend fun recordFailure(failure: Exception): DriveBackupFailure {
            if (failure is CancellationException) throw failure
            val record = DriveBackupFailure(clock.instant(), failure.backupFailureKind())
            preferences.recordFailure(record)
            return record
        }

        /**
         * Runs [action], and if Drive refuses the token, **asks for a fresh one silently and tries
         * once more**.
         *
         * An access token lives about an hour and a cached one can be refused before its time, so
         * a single 401 says nothing about whether the grant is gone. Only a silent authorization
         * that itself needs the owner — a [DriveGrant.Resolution] — means reconnecting is really
         * required; before this, one stale token stopped automatic backups until the owner
         * happened to open Settings.
         */
        private suspend fun <T> withToken(
            accessToken: String,
            action: suspend (String) -> T,
        ): T =
            try {
                action(accessToken)
            } catch (failure: DriveBackupException) {
                if (failure.kind != DriveBackupFailureKind.NEEDS_RECONNECTING) throw failure
                invalidate(accessToken)
                val renewed = authorization.authorize()
                if (renewed !is DriveGrant.Authorized) throw failure
                try {
                    action(renewed.accessToken)
                } catch (again: DriveBackupException) {
                    if (again.kind == DriveBackupFailureKind.NEEDS_RECONNECTING) invalidate(renewed.accessToken)
                    throw again
                }
            }

        private suspend fun invalidate(accessToken: String) {
            try {
                authorization.invalidateToken(accessToken)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            }
        }
    }

internal fun Exception.backupFailureKind(): DriveBackupFailureKind =
    (this as? DriveBackupException)?.kind ?: DriveBackupFailureKind.OTHER

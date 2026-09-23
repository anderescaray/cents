package io.github.anderescaray.cents.ui.backup

import android.content.Intent
import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.model.DriveBackupFailure
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.domain.model.DriveBackupState
import io.github.anderescaray.cents.domain.repository.DriveBackupRepository
import io.github.anderescaray.cents.domain.repository.DriveBackupStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class MemoryDriveBackupRepository(
    initial: DriveBackupState = DriveBackupState(),
) : DriveBackupRepository {
    val state = MutableStateFlow(initial)

    override fun observe() = state

    override suspend fun setAutomaticEnabled(enabled: Boolean) {
        state.value = state.value.copy(automaticEnabled = enabled)
    }

    override suspend fun recordSuccess(at: Instant) {
        state.value = state.value.copy(lastSuccessAt = at, lastFailure = null)
    }

    override suspend fun recordFailure(failure: DriveBackupFailure) {
        state.value = state.value.copy(lastFailure = failure)
    }

    override suspend fun clearReconnectRequired() {
        if (state.value.lastFailure?.kind == DriveBackupFailureKind.NEEDS_RECONNECTING) {
            state.value = state.value.copy(lastFailure = null)
        }
    }
}

class FakeDriveAuthorization : DriveAuthorization {
    var grant: DriveGrant = DriveGrant.Authorized("test-access-token")

    /** Handed out one per request before [grant] is, for a test that needs the answer to change. */
    val grants = ArrayDeque<DriveGrant>()
    var intentGrant: DriveGrant = DriveGrant.Authorized("resolved-access-token")
    var failure: Exception? = null
    var waiting: CompletableDeferred<DriveGrant>? = null
    var requests = 0
    val invalidated = mutableListOf<String>()

    override suspend fun authorize(): DriveGrant {
        requests++
        failure?.let { throw it }
        return waiting?.await() ?: grants.removeFirstOrNull() ?: grant
    }

    override fun fromIntent(intent: Intent): DriveGrant {
        failure?.let { throw it }
        return intentGrant
    }

    override suspend fun invalidateToken(accessToken: String) {
        invalidated += accessToken
    }
}

class FakeDriveBackupStorage : DriveBackupStorage {
    var files: List<DriveBackup> = emptyList()
    var contents: String = ""
    var failure: Exception? = null

    /** Thrown one per call before [failure] is, for a failure that happens once and then clears. */
    val failures = ArrayDeque<Exception>()
    var waiting: CompletableDeferred<Unit>? = null
    val tokens = mutableListOf<String>()
    var uploads = 0
    var downloads = 0

    override suspend fun create(accessToken: String) {
        tokens += accessToken
        failures.removeFirstOrNull()?.let { throw it }
        failure?.let { throw it }
        waiting?.await()
        uploads++
    }

    override suspend fun list(accessToken: String): List<DriveBackup> {
        tokens += accessToken
        failures.removeFirstOrNull()?.let { throw it }
        failure?.let { throw it }
        return files
    }

    override suspend fun download(
        accessToken: String,
        id: String,
    ): String {
        tokens += accessToken
        failures.removeFirstOrNull()?.let { throw it }
        failure?.let { throw it }
        downloads++
        waiting?.await()
        return contents
    }
}

class FakeDriveBackupScheduler : DriveBackupScheduler {
    val changes = mutableListOf<Boolean>()
    var failure: Exception? = null

    override suspend fun setEnabled(enabled: Boolean) {
        failure?.let { throw it }
        changes += enabled
    }
}

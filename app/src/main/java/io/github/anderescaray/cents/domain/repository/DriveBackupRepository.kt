package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.model.DriveBackupFailure
import io.github.anderescaray.cents.domain.model.DriveBackupState
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface DriveBackupRepository {
    fun observe(): Flow<DriveBackupState>

    suspend fun setAutomaticEnabled(enabled: Boolean)

    suspend fun recordSuccess(at: Instant)

    suspend fun recordFailure(failure: DriveBackupFailure)

    suspend fun clearReconnectRequired()
}

interface DriveBackupStorage {
    suspend fun create(accessToken: String)

    suspend fun list(accessToken: String): List<DriveBackup>

    suspend fun download(
        accessToken: String,
        id: String,
    ): String
}

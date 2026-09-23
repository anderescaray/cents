package io.github.anderescaray.cents.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.anderescaray.cents.domain.model.DriveBackupFailure
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.domain.model.DriveBackupState
import io.github.anderescaray.cents.domain.repository.DriveBackupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreDriveBackupRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : DriveBackupRepository {
        override fun observe(): Flow<DriveBackupState> =
            dataStore.data.map { preferences ->
                val failureTime = preferences[FAILURE_AT]
                val failureKind = preferences[FAILURE_KIND]
                DriveBackupState(
                    automaticEnabled = preferences[AUTOMATIC_ENABLED] ?: false,
                    lastSuccessAt = preferences[SUCCESS_AT]?.let(Instant::ofEpochMilli),
                    lastFailure =
                        if (failureTime != null && failureKind != null) {
                            DriveBackupFailure(
                                at = Instant.ofEpochMilli(failureTime),
                                kind =
                                    DriveBackupFailureKind.entries.find { it.name == failureKind }
                                        ?: DriveBackupFailureKind.OTHER,
                            )
                        } else {
                            null
                        },
                )
            }

        override suspend fun setAutomaticEnabled(enabled: Boolean) {
            dataStore.edit { it[AUTOMATIC_ENABLED] = enabled }
        }

        override suspend fun recordSuccess(at: Instant) {
            dataStore.edit {
                it[SUCCESS_AT] = at.toEpochMilli()
                it.remove(FAILURE_AT)
                it.remove(FAILURE_KIND)
            }
        }

        override suspend fun recordFailure(failure: DriveBackupFailure) {
            dataStore.edit {
                it[FAILURE_AT] = failure.at.toEpochMilli()
                it[FAILURE_KIND] = failure.kind.name
            }
        }

        override suspend fun clearReconnectRequired() {
            dataStore.edit {
                if (it[FAILURE_KIND] == DriveBackupFailureKind.NEEDS_RECONNECTING.name) {
                    it.remove(FAILURE_AT)
                    it.remove(FAILURE_KIND)
                }
            }
        }

        private companion object {
            val AUTOMATIC_ENABLED = booleanPreferencesKey("driveBackup.automaticEnabled")
            val SUCCESS_AT = longPreferencesKey("driveBackup.lastSuccessAt")
            val FAILURE_AT = longPreferencesKey("driveBackup.lastFailureAt")
            val FAILURE_KIND = stringPreferencesKey("driveBackup.lastFailureKind")
        }
    }

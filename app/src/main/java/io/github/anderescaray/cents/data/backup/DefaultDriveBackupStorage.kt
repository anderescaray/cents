package io.github.anderescaray.cents.data.backup

import io.github.anderescaray.cents.data.export.BackupWriter
import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.repository.BackupRepository
import io.github.anderescaray.cents.domain.repository.DriveBackupStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDriveBackupStorage
    @Inject
    constructor(
        private val backups: BackupRepository,
        private val files: DriveFiles,
    ) : DriveBackupStorage {
        private val uploadLock = Mutex()

        override suspend fun create(accessToken: String) {
            uploadLock.withLock {
                withContext(Dispatchers.IO) {
                    val snapshot = backups.snapshot()
                    files.upload(accessToken, driveBackupFileName(snapshot.exportedAt), BackupWriter.toJson(snapshot))
                    driveBackupIdsToDelete(files.list(accessToken)).forEach { id ->
                        currentCoroutineContext().ensureActive()
                        files.delete(accessToken, id)
                    }
                }
            }
        }

        override suspend fun list(accessToken: String): List<DriveBackup> = files.list(accessToken)

        override suspend fun download(
            accessToken: String,
            id: String,
        ): String = files.download(accessToken, id)
    }

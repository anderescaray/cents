package io.github.anderescaray.cents.data.backup

import io.github.anderescaray.cents.data.export.BackupWriter
import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.repository.BackupRepository
import io.github.anderescaray.cents.domain.repository.DriveBackupStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
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

        /**
         * Uploads a copy unless the newest one already holds exactly this, then prunes.
         *
         * Pruning happens **after** the copy is safe and **cannot fail the backup**: a failed
         * delete used to fail the run, the run was retried, and every retry uploaded another copy.
         * Whatever is left over is pruned by the next run instead.
         */
        override suspend fun create(accessToken: String) {
            uploadLock.withLock {
                withContext(Dispatchers.IO) {
                    val snapshot = backups.snapshot()
                    val fingerprint = fingerprintOf(snapshot)
                    val existing = files.list(accessToken)
                    val current =
                        if (existing.minWithOrNull(driveBackupOrder)?.fingerprint == fingerprint) {
                            existing
                        } else {
                            val name = driveBackupFileName(snapshot.exportedAt)
                            val contents = BackupWriter.toJson(snapshot)
                            val id = files.upload(accessToken, name, contents, fingerprint)
                            val uploaded =
                                DriveBackup(
                                    id = id,
                                    name = name,
                                    createdAt = snapshot.exportedAt,
                                    sizeBytes = contents.toByteArray().size.toLong(),
                                    fingerprint = fingerprint,
                                )
                            listOf(uploaded) + existing
                        }
                    prune(accessToken, current)
                }
            }
        }

        override suspend fun list(accessToken: String): List<DriveBackup> = files.list(accessToken)

        override suspend fun download(
            accessToken: String,
            id: String,
        ): String = files.download(accessToken, id)

        private suspend fun prune(
            accessToken: String,
            current: List<DriveBackup>,
        ) {
            try {
                driveBackupIdsToDelete(current).forEach { id ->
                    currentCoroutineContext().ensureActive()
                    files.delete(accessToken, id)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            }
        }
    }

/**
 * What a backup holds, ignoring when it was taken: two nights with nothing recorded between them
 * give the same fingerprint, and the second copy is not uploaded.
 */
internal fun fingerprintOf(snapshot: Backup): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(BackupWriter.toJson(snapshot.copy(exportedAt = Instant.EPOCH)).toByteArray())
        .joinToString("") { "%02x".format(it) }

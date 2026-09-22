package io.github.anderescaray.cents.domain.model

import java.time.Instant

data class DriveBackup(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val sizeBytes: Long,
)

enum class DriveBackupFailureKind {
    NEEDS_RECONNECTING,
    NETWORK,
    OTHER,
}

data class DriveBackupFailure(
    val at: Instant,
    val kind: DriveBackupFailureKind,
)

data class DriveBackupState(
    val automaticEnabled: Boolean = false,
    val lastSuccessAt: Instant? = null,
    val lastFailure: DriveBackupFailure? = null,
)

class DriveBackupException(
    val kind: DriveBackupFailureKind,
) : Exception(kind.name)

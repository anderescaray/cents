package io.github.anderescaray.cents.domain.model

import java.time.Instant

data class DriveBackup(
    val id: String,
    val name: String,
    val createdAt: Instant,
    /** Null when Drive leaves the size out, which it may; a missing size is not a broken file. */
    val sizeBytes: Long?,
    /**
     * What the copy holds, as a hash that ignores when it was written. Null for a copy uploaded
     * before this was recorded, which is then never mistaken for an identical one.
     */
    val fingerprint: String? = null,
)

enum class DriveBackupFailureKind {
    NEEDS_RECONNECTING,
    NETWORK,

    /** Drive has no room left. Retrying cannot help until the owner frees some. */
    DRIVE_FULL,
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

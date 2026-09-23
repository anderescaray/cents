package io.github.anderescaray.cents.ui.backup

import android.app.PendingIntent
import android.content.Intent

sealed interface DriveGrant {
    class Authorized(
        val accessToken: String,
    ) : DriveGrant

    data class Resolution(
        val pendingIntent: PendingIntent,
    ) : DriveGrant
}

interface DriveAuthorization {
    suspend fun authorize(): DriveGrant

    fun fromIntent(intent: Intent): DriveGrant

    suspend fun invalidateToken(accessToken: String)
}

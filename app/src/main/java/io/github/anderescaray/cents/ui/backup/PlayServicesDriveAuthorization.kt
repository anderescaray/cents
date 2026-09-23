package io.github.anderescaray.cents.ui.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anderescaray.cents.domain.model.DriveBackupException
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlayServicesDriveAuthorization
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DriveAuthorization {
        private val client = Identity.getAuthorizationClient(context)

        override suspend fun authorize(): DriveGrant = client.authorize(request()).awaitResult().toGrant()

        override fun fromIntent(intent: Intent): DriveGrant =
            try {
                client.getAuthorizationResultFromIntent(intent).toGrant()
            } catch (failure: ApiException) {
                throw failure.toBackupException()
            }

        override suspend fun invalidateToken(accessToken: String) {
            client.clearToken(ClearTokenRequest.builder().setToken(accessToken).build()).awaitResult()
        }

        private fun AuthorizationResult.toGrant(): DriveGrant {
            if (hasResolution()) {
                return DriveGrant.Resolution(
                    pendingIntent ?: throw DriveBackupException(DriveBackupFailureKind.OTHER),
                )
            }
            val token = accessToken
            if (token.isNullOrBlank() || DRIVE_SCOPE !in grantedScopes) {
                throw DriveBackupException(DriveBackupFailureKind.NEEDS_RECONNECTING)
            }
            return DriveGrant.Authorized(token)
        }

        internal companion object {
            const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

            fun request(): AuthorizationRequest =
                AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build()
        }
    }

/**
 * A task Play services cancels is a failed authorization, not a cancelled coroutine: surfacing it
 * as a [kotlinx.coroutines.CancellationException] would slip past every failure handler and leave
 * the screen showing *Connecting…* for good.
 */
internal suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener(DIRECT_EXECUTOR) { continuation.resume(it) }
        addOnFailureListener(DIRECT_EXECUTOR) {
            continuation.resumeWithException(
                if (it is ApiException) it.toBackupException() else DriveBackupException(DriveBackupFailureKind.OTHER),
            )
        }
        addOnCanceledListener(DIRECT_EXECUTOR) {
            continuation.resumeWithException(DriveBackupException(DriveBackupFailureKind.OTHER))
        }
    }

private fun ApiException.toBackupException(): DriveBackupException =
    DriveBackupException(
        when (statusCode) {
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> {
                DriveBackupFailureKind.NETWORK
            }

            CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.RESOLUTION_REQUIRED -> {
                DriveBackupFailureKind.NEEDS_RECONNECTING
            }

            else -> {
                DriveBackupFailureKind.OTHER
            }
        },
    )

private val DIRECT_EXECUTOR = Executor { it.run() }

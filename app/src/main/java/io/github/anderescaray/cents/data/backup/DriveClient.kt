package io.github.anderescaray.cents.data.backup

import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.model.DriveBackupException
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.coroutines.resumeWithException

interface DriveFiles {
    suspend fun upload(
        accessToken: String,
        name: String,
        contents: String,
        fingerprint: String,
    ): String

    suspend fun list(accessToken: String): List<DriveBackup>

    suspend fun download(
        accessToken: String,
        id: String,
    ): String

    suspend fun delete(
        accessToken: String,
        id: String,
    )
}

class DriveClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://www.googleapis.com/".toHttpUrl(),
) : DriveFiles {
    override suspend fun upload(
        accessToken: String,
        name: String,
        contents: String,
        fingerprint: String,
    ): String =
        withContext(Dispatchers.IO) {
            val metadata =
                json.encodeToString(
                    UploadMetadata(name, listOf("appDataFolder"), mapOf(FINGERPRINT_PROPERTY to fingerprint)),
                )
            val body =
                MultipartBody
                    .Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(metadata.toRequestBody(JSON_MEDIA_TYPE))
                    .addPart(contents.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            val url =
                baseUrl
                    .newBuilder()
                    .addPathSegments("upload/drive/v3/files")
                    .addQueryParameter("uploadType", "multipart")
                    .addQueryParameter("fields", "id")
                    .build()
            val response = request(authorized(url, accessToken).post(body).build())
            json.decodeFromString<UploadedFile>(response).id.also { require(it.isNotBlank()) }
        }

    override suspend fun list(accessToken: String): List<DriveBackup> =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<DriveBackup>()
            val seenPages = mutableSetOf<String>()
            var pageToken: String? = null
            do {
                val url =
                    filesUrl()
                        .newBuilder()
                        .addQueryParameter("spaces", "appDataFolder")
                        .addQueryParameter("fields", "nextPageToken,files(id,name,createdTime,size,appProperties)")
                        .addQueryParameter("orderBy", "createdTime desc")
                        .addQueryParameter("q", "trashed = false")
                        .addQueryParameter("pageSize", PAGE_SIZE.toString())
                        .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                        .build()
                val page = json.decodeFromString<FilePage>(request(authorized(url, accessToken).build()))
                files += page.files.filter { isDriveBackupFile(it.name) }.mapNotNull { it.toBackup() }
                pageToken = page.nextPageToken?.takeIf { it.isNotEmpty() }
                check(pageToken == null || seenPages.add(pageToken))
            } while (pageToken != null)
            files.distinctBy { it.id }.sortedWith(driveBackupOrder)
        }

    override suspend fun download(
        accessToken: String,
        id: String,
    ): String {
        val url =
            filesUrl()
                .newBuilder()
                .addPathSegment(id)
                .addQueryParameter("alt", "media")
                .build()
        return request(authorized(url, accessToken).build())
    }

    override suspend fun delete(
        accessToken: String,
        id: String,
    ) {
        val url = filesUrl().newBuilder().addPathSegment(id).build()
        request(authorized(url, accessToken).delete().build())
    }

    private fun filesUrl(): HttpUrl = baseUrl.newBuilder().addPathSegments("drive/v3/files").build()

    private fun authorized(
        url: HttpUrl,
        accessToken: String,
    ): Request.Builder = Request.Builder().url(url).header("Authorization", "Bearer $accessToken")

    private suspend fun request(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        continuation.resumeWithException(DriveBackupException(DriveBackupFailureKind.NETWORK))
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        continuation.resumeWith(
                            runCatching { response.use { it.backupBody(request.method) } }
                                .recoverCatching { failure ->
                                    if (failure is IOException) {
                                        throw DriveBackupException(DriveBackupFailureKind.NETWORK)
                                    }
                                    throw failure
                                },
                        )
                    }
                },
            )
        }

    private fun Response.backupBody(method: String): String =
        when {
            code == HTTP_UNAUTHORIZED -> {
                throw DriveBackupException(DriveBackupFailureKind.NEEDS_RECONNECTING)
            }

            code == HTTP_TOO_MANY_REQUESTS || code in HTTP_INTERNAL_ERROR..LAST_SERVER_ERROR -> {
                throw DriveBackupException(DriveBackupFailureKind.NETWORK)
            }

            code == HTTP_FORBIDDEN -> {
                throw DriveBackupException(forbiddenKind(body.string()))
            }

            code == HTTP_NOT_FOUND && method == "DELETE" -> {
                ""
            }

            !isSuccessful -> {
                throw DriveBackupException(DriveBackupFailureKind.OTHER)
            }

            else -> {
                body.string()
            }
        }

    internal companion object {
        /** The app property a copy's [DriveBackup.fingerprint] is stored under. */
        const val FINGERPRINT_PROPERTY = "contentSha256"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
        private const val PAGE_SIZE = 1000
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val LAST_SERVER_ERROR = 599
        private val FULL_REASONS = setOf("storageQuotaExceeded")
        private val RATE_REASONS =
            setOf("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded", "sharingRateLimitExceeded")
        private val PERMISSION_REASONS = setOf("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT")

        /**
         * A 403 from Drive is several different answers under one code, told apart only by the
         * reasons in its body: a full Drive, a rate limit, or a grant missing the scope. Each wants
         * a different response, so each gets its own kind; anything else stays [OTHER].
         */
        internal fun forbiddenKind(body: String): DriveBackupFailureKind {
            val reasons = errorReasons(body)
            return when {
                reasons.any { it in FULL_REASONS } -> DriveBackupFailureKind.DRIVE_FULL
                reasons.any { it in PERMISSION_REASONS } -> DriveBackupFailureKind.NEEDS_RECONNECTING
                reasons.any { it in RATE_REASONS } -> DriveBackupFailureKind.NETWORK
                else -> DriveBackupFailureKind.OTHER
            }
        }

        /** Both shapes Google uses: `error.errors[].reason` and the newer `error.details[].reason`. */
        private fun errorReasons(body: String): Set<String> {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
            val error = (root as? JsonObject)?.get("error") as? JsonObject
            return listOf("errors", "details")
                .flatMap { key -> (error?.get(key) as? JsonArray).orEmpty() }
                .mapNotNull { ((it as? JsonObject)?.get("reason") as? JsonPrimitive)?.content }
                .toSet()
        }
    }
}

@Serializable
private data class UploadMetadata(
    val name: String,
    val parents: List<String>,
    val appProperties: Map<String, String>,
)

@Serializable
private data class UploadedFile(
    val id: String,
)

@Serializable
private data class FilePage(
    val files: List<DriveFile> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
private data class DriveFile(
    val id: String,
    val name: String,
    val createdTime: String,
    val size: String? = null,
    val appProperties: Map<String, String>? = null,
) {
    /**
     * One odd entry is left out rather than failing the whole listing: a missing size only costs
     * the row its subtitle, and a copy with no usable id or date cannot be offered anyway.
     */
    fun toBackup(): DriveBackup? {
        val created =
            try {
                Instant.parse(createdTime)
            } catch (_: DateTimeParseException) {
                null
            }
        return if (id.isBlank() || created == null) {
            null
        } else {
            DriveBackup(
                id = id,
                name = name,
                createdAt = created,
                sizeBytes = size?.toLongOrNull()?.takeIf { it >= 0 },
                fingerprint = appProperties?.get(DriveClient.FINGERPRINT_PROPERTY),
            )
        }
    }
}

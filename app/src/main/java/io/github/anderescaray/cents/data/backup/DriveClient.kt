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
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.time.Instant
import kotlin.coroutines.resumeWithException

interface DriveFiles {
    suspend fun upload(
        accessToken: String,
        name: String,
        contents: String,
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
    ): String =
        withContext(Dispatchers.IO) {
            val metadata = json.encodeToString(UploadMetadata(name, listOf("appDataFolder")))
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
                        .addQueryParameter("fields", "nextPageToken,files(id,name,createdTime,size)")
                        .addQueryParameter("orderBy", "createdTime desc")
                        .addQueryParameter("q", "trashed = false")
                        .addQueryParameter("pageSize", PAGE_SIZE.toString())
                        .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                        .build()
                val page = json.decodeFromString<FilePage>(request(authorized(url, accessToken).build()))
                files += page.files.filter { isDriveBackupFile(it.name) }.map { it.toBackup() }
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

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
        const val PAGE_SIZE = 1000
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val LAST_SERVER_ERROR = 599
    }
}

@Serializable
private data class UploadMetadata(
    val name: String,
    val parents: List<String>,
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
) {
    fun toBackup(): DriveBackup {
        val bytes = requireNotNull(size).toLong()
        require(id.isNotBlank() && bytes >= 0)
        return DriveBackup(id, name, Instant.parse(createdTime), bytes)
    }
}

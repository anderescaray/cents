package io.github.anderescaray.cents.data.backup

import io.github.anderescaray.cents.domain.model.DriveBackupException
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class DriveClientTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var drive: DriveClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        http =
            OkHttpClient
                .Builder()
                .retryOnConnectionFailure(false)
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
        drive = DriveClient(http, server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdown()
    }

    @Test
    fun `upload is related multipart with appDataFolder metadata and an unchanged JSON body`() =
        runTest {
            server.enqueue(response("""{"id":"uploaded"}"""))
            val contents = """{"schemaVersion":6,"accounts":[],"note":"España"}"""
            val name = "cents-backup-2026-09-22T031245.json"

            assertEquals("uploaded", drive.upload("ephemeral-token", name, contents, "fingerprint"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/upload/drive/v3/files", request.url.encodedPath)
            assertEquals("multipart", request.url.queryParameter("uploadType"))
            assertEquals("Bearer ephemeral-token", request.headers["Authorization"])
            assertFalse(request.url.toString().contains("ephemeral-token"))
            val type = requireNotNull(request.headers["Content-Type"])
            assertTrue(type.startsWith("multipart/related; boundary="))
            val boundary = type.substringAfter("boundary=")
            val parts = requireNotNull(request.body).utf8().split("--$boundary")
            val metadata = Json.parseToJsonElement(parts[1].substringAfter("\r\n\r\n").trim()).jsonObject
            assertEquals(name, metadata.getValue("name").jsonPrimitive.content)
            assertEquals(
                "appDataFolder",
                metadata
                    .getValue("parents")
                    .jsonArray
                    .single()
                    .jsonPrimitive.content,
            )
            assertEquals(
                "fingerprint",
                metadata
                    .getValue("appProperties")
                    .jsonObject
                    .getValue("contentSha256")
                    .jsonPrimitive.content,
            )
            assertEquals(contents, parts[2].substringAfter("\r\n\r\n").removeSuffix("\r\n"))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `listing reads every page and orders backups newest first`() =
        runTest {
            server.enqueue(
                response("""{"files":[${file("old", "2026-09-20T01:00:00Z")}],"nextPageToken":"page two"}"""),
            )
            server.enqueue(
                response(
                    """{"files":[${file("new", "2026-09-22T01:00:00Z")},${file("middle", "2026-09-21T01:00:00Z")}]}""",
                ),
            )

            val files = drive.list("list-token")

            assertEquals(listOf("new", "middle", "old"), files.map { it.id })
            assertEquals(Instant.parse("2026-09-22T01:00:00Z"), files.first().createdAt)
            assertEquals(2048L, files.first().sizeBytes)
            val first = server.takeRequest()
            assertEquals("appDataFolder", first.url.queryParameter("spaces"))
            assertEquals("createdTime desc", first.url.queryParameter("orderBy"))
            assertEquals(
                "nextPageToken,files(id,name,createdTime,size,appProperties)",
                first.url.queryParameter("fields"),
            )
            assertEquals("Bearer list-token", first.headers["Authorization"])
            assertEquals("page two", server.takeRequest().url.queryParameter("pageToken"))
        }

    @Test
    fun `unrelated app data files are not offered or exposed to retention`() =
        runTest {
            val backup = file("backup", "2026-09-22T01:00:00Z")
            val unrelated = """{"id":"other","name":"unrelated-folder","createdTime":"2026-09-23T00:00:00Z"}"""
            server.enqueue(response("""{"files":[$backup,$unrelated]}"""))
            assertEquals(listOf("backup"), drive.list("token").map { it.id })
        }

    @Test
    fun `download returns the entire body with a fresh bearer header`() =
        runTest {
            val contents = "{\n  \"accounts\": []\n}\n"
            server.enqueue(response(contents))

            assertEquals(contents, drive.download("download-token", "chosen-id"))

            val request = server.takeRequest()
            assertEquals("/drive/v3/files/chosen-id", request.url.encodedPath)
            assertEquals("media", request.url.queryParameter("alt"))
            assertEquals("Bearer download-token", request.headers["Authorization"])
        }

    @Test
    fun `delete addresses only the selected id and accepts an already deleted file`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(204).build())
            server.enqueue(MockResponse.Builder().code(404).build())
            drive.delete("token", "old-id")
            drive.delete("token", "old-id")

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/drive/v3/files/old-id", request.url.encodedPath)
            assertEquals("Bearer token", request.headers["Authorization"])
        }

    @Test
    fun `a 401 requires reconnecting without leaking response contents`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(401)
                    .body("private server response")
                    .build(),
            )
            val failure = runCatching { drive.download("token", "file") }.exceptionOrNull()
            assertEquals(DriveBackupFailureKind.NEEDS_RECONNECTING, (failure as DriveBackupException).kind)
            assertFalse(failure.toString().contains("private server response"))
        }

    @Test
    fun `server and rate limit failures are retryable network failures`() =
        runTest {
            for (code in listOf(429, 500, 503)) {
                server.enqueue(MockResponse.Builder().code(code).build())
                val failure = runCatching { drive.list("token") }.exceptionOrNull()
                assertEquals(DriveBackupFailureKind.NETWORK, (failure as DriveBackupException).kind)
            }
        }

    @Test
    fun `an IOException is a retryable network failure`() =
        runTest {
            server.close()
            val failure = runCatching { drive.download("token", "file") }.exceptionOrNull()
            assertEquals(DriveBackupFailureKind.NETWORK, (failure as DriveBackupException).kind)
        }

    @Test
    fun `other HTTP failures are reported without claiming the grant expired`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(403).build())
            val failure = runCatching { drive.list("token") }.exceptionOrNull()
            assertEquals(DriveBackupFailureKind.OTHER, (failure as DriveBackupException).kind)
        }

    @Test
    fun `a full Drive is told apart from other refusals`() =
        runTest {
            server.enqueue(forbidden("storageQuotaExceeded"))
            val failure = runCatching { drive.upload("token", "name", "{}", "fingerprint") }.exceptionOrNull()
            assertEquals(DriveBackupFailureKind.DRIVE_FULL, (failure as DriveBackupException).kind)
        }

    @Test
    fun `a rate limit refusal is a retryable network failure`() =
        runTest {
            for (reason in listOf("rateLimitExceeded", "userRateLimitExceeded")) {
                server.enqueue(forbidden(reason))
                val failure = runCatching { drive.list("token") }.exceptionOrNull()
                assertEquals(DriveBackupFailureKind.NETWORK, (failure as DriveBackupException).kind)
            }
        }

    @Test
    fun `a grant missing the scope needs reconnecting in either error shape`() =
        runTest {
            server.enqueue(forbidden("insufficientPermissions"))
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(403)
                    .body("""{"error":{"code":403,"details":[{"reason":"ACCESS_TOKEN_SCOPE_INSUFFICIENT"}]}}""")
                    .build(),
            )
            repeat(2) {
                val failure = runCatching { drive.list("token") }.exceptionOrNull()
                assertEquals(DriveBackupFailureKind.NEEDS_RECONNECTING, (failure as DriveBackupException).kind)
            }
        }

    @Test
    fun `a 403 whose body is not JSON is still only a failure`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(403)
                    .body("<html>forbidden</html>")
                    .build(),
            )
            val failure = runCatching { drive.list("token") }.exceptionOrNull()
            assertEquals(DriveBackupFailureKind.OTHER, (failure as DriveBackupException).kind)
        }

    @Test
    fun `a file without a size is still listed and one without an id is left out`() =
        runTest {
            val noSize =
                """{"id":"no-size","name":"cents-backup-2026-09-22T010000.json","createdTime":"2026-09-22T01:00:00Z"}"""
            val noId = """{"id":"","name":"cents-backup-2026-09-21T010000.json","createdTime":"2026-09-21T01:00:00Z"}"""
            server.enqueue(response("""{"files":[$noSize,$noId,${file("sized", "2026-09-20T01:00:00Z")}]}"""))

            val files = drive.list("token")

            assertEquals(listOf("no-size", "sized"), files.map { it.id })
            assertNull(files.first().sizeBytes)
            assertEquals(2048L, files.last().sizeBytes)
        }

    @Test
    fun `a copy's fingerprint is read back from its app properties`() =
        runTest {
            val withFingerprint =
                """{"id":"a","name":"cents-backup-2026-09-22T010000.json","createdTime":"2026-09-22T01:00:00Z",""" +
                    """"appProperties":{"contentSha256":"abc"}}"""
            server.enqueue(response("""{"files":[$withFingerprint,${file("b", "2026-09-21T01:00:00Z")}]}"""))

            assertEquals(listOf("abc", null), drive.list("token").map { it.fingerprint })
        }

    private fun forbidden(reason: String) =
        MockResponse
            .Builder()
            .code(403)
            .body("""{"error":{"code":403,"errors":[{"domain":"usageLimits","reason":"$reason"}]}}""")
            .build()

    private fun response(body: String) = MockResponse.Builder().body(body).build()

    private fun file(
        id: String,
        created: String,
    ): String =
        """{"id":"$id","name":"cents-backup-${created.take(
            10,
        )}T010000.json","createdTime":"$created","size":"2048","ignored":true}"""
}

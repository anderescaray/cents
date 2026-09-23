package io.github.anderescaray.cents.ui.backup

import com.google.android.gms.tasks.Tasks
import io.github.anderescaray.cents.domain.model.DriveBackupException
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveAuthorizationTest {
    @Test
    fun `authorization requests only hidden app data access`() {
        assertEquals(
            listOf("https://www.googleapis.com/auth/drive.appdata"),
            PlayServicesDriveAuthorization.request().requestedScopes.map { it.scopeUri },
        )
    }

    @Test
    fun `a task Play services cancels is a failure rather than a cancelled coroutine`() =
        runTest {
            val failure = runCatching { Tasks.forCanceled<String>().awaitResult() }.exceptionOrNull()

            // A CancellationException here would slip past every failure handler and leave the
            // screen on "Connecting to Google Drive…" for good.
            assertEquals(DriveBackupFailureKind.OTHER, (failure as DriveBackupException).kind)
        }

    @Test
    fun `an authorization result does not print its token`() {
        assertFalse(DriveGrant.Authorized("sensitive-access-token").toString().contains("sensitive-access-token"))
    }
}

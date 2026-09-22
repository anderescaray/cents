package io.github.anderescaray.cents.ui.backup

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
    fun `an authorization result does not print its token`() {
        assertFalse(DriveGrant.Authorized("sensitive-access-token").toString().contains("sensitive-access-token"))
    }
}

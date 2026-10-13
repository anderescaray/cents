package io.github.anderescaray.cents.ui.capture

import android.app.Application
import android.app.Notification
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The apps Settings › Capture offers. Found on the owner's phone (`FEEDBACK.md`, 2026-10-12): their
 * bank's app was not in the list at all, so its payments could not be captured.
 */
@RunWith(RobolectricTestRunner::class)
class InstalledAppsTest {
    private lateinit var context: Application
    private var notifying: Map<String, String> = emptyMap()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `an app is named as the launcher names it, under its icon`() =
        runTest {
            launcherApp("es.example.bank", appLabel = "Banking SDK host", iconLabel = "Ruralvía")

            assertEquals(listOf(InstalledApp("es.example.bank", "Ruralvía")), apps())
        }

    @Test
    fun `an app with two icons is listed under both names`() =
        runTest {
            launcherApp("es.example.bank", appLabel = "Bank", iconLabel = "Ruralvía")
            launcherApp("es.example.bank", appLabel = "Bank", iconLabel = "Bizum", activity = "Bizum")

            assertEquals(
                listOf(InstalledApp("es.example.bank", "Bizum"), InstalledApp("es.example.bank", "Ruralvía")),
                apps(),
            )
        }

    @Test
    fun `an app notifying now is offered, under its name, even when the launcher does not list it`() =
        runTest {
            launcherApp("com.example.games", appLabel = "Games", iconLabel = "Games")
            notifying = mapOf("es.example.bank" to "Ruralvía", "com.example.games" to "Games")

            assertEquals(
                listOf(InstalledApp("com.example.games", "Games"), InstalledApp("es.example.bank", "Ruralvía")),
                apps(),
            )
        }

    @Test
    fun `Cents itself is never offered`() =
        runTest {
            launcherApp(context.packageName, appLabel = "Cents", iconLabel = "Cents")
            notifying = mapOf(context.packageName to "Cents")

            assertTrue(apps().none { it.packageName == context.packageName })
        }

    @Test
    fun `a notification names its app, without asking the package manager`() {
        val carried = ApplicationInfo().apply { nonLocalizedLabel = "Ruralvía" }
        val named =
            posted(
                "es.example.bank",
                Notification.Builder(context, "c").build().apply {
                    extras.putParcelable("android.appInfo", carried)
                },
            )
        val unnamed =
            posted(
                "es.example.other",
                Notification.Builder(context, "c").build().apply { extras.remove("android.appInfo") },
            )

        assertEquals("Ruralvía", named.appName(context.packageManager))
        assertEquals("es.example.other", unnamed.appName(context.packageManager))
    }

    @Test
    fun `only notifications capture could read are offered, and none while the listener is away`() {
        val listener = Robolectric.buildService(OnScreen::class.java).create().get()
        listener.active =
            arrayOf(
                posted("es.example.bank", Notification.Builder(context, "c").build()),
                posted("com.example.music", Notification.Builder(context, "c").setOngoing(true).build()),
            )
        val notifyingApps = ListenerNotifyingApps()

        assertTrue(notifyingApps.apps().isEmpty())
        notifyingApps.attach(listener)
        assertEquals(setOf("es.example.bank"), notifyingApps.apps().keys)
        notifyingApps.detach(listener)
        assertTrue(notifyingApps.apps().isEmpty())
    }

    /** A listener whose screen holds whatever the test puts there. */
    class OnScreen : NotificationListenerService() {
        var active: Array<StatusBarNotification> = emptyArray()

        override fun getActiveNotifications(): Array<StatusBarNotification> = active
    }

    private suspend fun apps(): List<InstalledApp> = PackageManagerInstalledApps(context) { notifying }.choosable()

    /** The public constructor is the deprecated one; the others are the system's own. */
    @Suppress("DEPRECATION")
    private fun posted(
        packageName: String,
        notification: Notification,
    ) = StatusBarNotification(packageName, packageName, 1, null, 0, 0, 0, notification, Process.myUserHandle(), 0L)

    private fun launcherApp(
        packageName: String,
        appLabel: String,
        iconLabel: String,
        activity: String = "Main",
    ) {
        val application =
            ApplicationInfo().apply {
                this.packageName = packageName
                nonLocalizedLabel = appLabel
            }
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = application
            },
        )
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            ResolveInfo().apply {
                nonLocalizedLabel = iconLabel
                activityInfo =
                    ActivityInfo().apply {
                        this.packageName = packageName
                        name = "$packageName.$activity"
                        applicationInfo = application
                    }
            },
        )
    }
}

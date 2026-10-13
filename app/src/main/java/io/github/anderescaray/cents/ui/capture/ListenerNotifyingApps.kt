package io.github.anderescaray.cents.ui.capture

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/** A notification capture never reads: one that stays on screen while something runs. */
internal const val IGNORED_NOTIFICATION_FLAGS = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE

/**
 * The apps with a notification on screen now, asked of [CaptureListenerService] while the system
 * has it connected, for Settings › Capture's list of apps ([PackageManagerInstalledApps]).
 *
 * Package names and app names only: no notification's text is read for it, and nothing is kept.
 * Empty while the listener is not connected, as before *Notification access* is granted. The
 * notifications capture never reads, ongoing ones and group summaries, are left out, so what is
 * offered is what capture could read.
 */
@Singleton
class ListenerNotifyingApps
    @Inject
    constructor() : NotifyingApps {
        @Volatile private var listener: WeakReference<NotificationListenerService>? = null

        fun attach(service: NotificationListenerService) {
            listener = WeakReference(service)
        }

        fun detach(service: NotificationListenerService) {
            if (listener?.get() === service) listener = null
        }

        override fun apps(): Map<String, String> {
            val service = listener?.get()
            val active =
                try {
                    service?.activeNotifications.orEmpty()
                } catch (refused: SecurityException) {
                    // The system let go of the listener before it said so.
                    Log.w(TAG, "the listener could not list what is on screen", refused)
                    emptyArray()
                }
            return active
                .filter {
                    (it.notification.flags and (IGNORED_NOTIFICATION_FLAGS or Notification.FLAG_GROUP_SUMMARY)) ==
                        0
                }.associate { it.packageName to it.appName(service?.packageManager) }
        }

        private companion object {
            const val TAG = "Capture"
        }
    }

/**
 * The name of the app that posted this, as the notification carries it.
 *
 * Asked of the notification rather than of the package manager, because since Android 11 Cents
 * may not see an app its manifest does not ask about, and the app this is for is the one missing
 * from the launcher's list. Its package when nothing better is there.
 */
internal fun StatusBarNotification.appName(manager: PackageManager?): String {
    val carried = BundleCompat.getParcelable(notification.extras, EXTRA_APP_INFO, ApplicationInfo::class.java)
    val label = if (carried != null && manager != null) runCatching { carried.loadLabel(manager) }.getOrNull() else null
    return label?.toString()?.takeIf { it.isNotBlank() } ?: packageName
}

/** Where a notification keeps the poster's `ApplicationInfo` (`Notification.EXTRA_BUILDER_APPLICATION_INFO`). */
private const val EXTRA_APP_INFO = "android.appInfo"

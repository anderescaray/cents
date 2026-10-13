package io.github.anderescaray.cents.ui.capture

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import javax.inject.Inject

/** One app the owner could choose for capture: what the phone calls it, and its package. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

/**
 * The apps on the phone that capture could read (`DECISIONS.md` D29).
 *
 * An interface so the picker's view model is tested without a package manager to ask.
 */
fun interface InstalledApps {
    /**
     * Every app with an icon in the launcher, and every app with a notification on screen now,
     * except Cents itself, sorted by name.
     */
    suspend fun choosable(): List<InstalledApp>
}

/** The apps with a notification on screen now, by package, each with its name. */
fun interface NotifyingApps {
    fun apps(): Map<String, String>
}

/**
 * Asks the package manager for the launcher's apps, and the listener for the apps notifying now.
 *
 * **Launcher apps**, because those are the ones the owner recognises: a bank's app has an icon,
 * while the hundreds of system packages behind them never post a payment. Each is named as the
 * launcher names it, under its icon, which is what the owner looks for: an app's own label can be
 * something else entirely, and an app with two icons is listed under both names. Since Android 11
 * the question is only answered for what the manifest's `<queries>` declares, and that block asks
 * for exactly this intent.
 *
 * **And the apps with a notification on screen now**, because a bank's app was missing from the
 * launcher's list on the owner's phone (`FEEDBACK.md`, 2026-10-12). Whatever the reason, an app
 * that is notifying is one capture can read, so opening the list while its notification shows
 * always finds it.
 */
class PackageManagerInstalledApps
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val notifying: NotifyingApps,
    ) : InstalledApps {
        override suspend fun choosable(): List<InstalledApp> =
            withContext(Dispatchers.IO) {
                val manager = context.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val listed =
                    manager
                        .queryIntentActivities(launcher, 0)
                        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
                        .distinct()
                val listedPackages = listed.mapTo(HashSet()) { it.packageName }
                val notifyingNow =
                    notifying
                        .apps()
                        .filterKeys { it !in listedPackages }
                        .map { (packageName, label) -> InstalledApp(packageName, label) }
                val collator = Collator.getInstance()
                (listed + notifyingNow)
                    .filter { it.packageName != context.packageName }
                    .sortedWith(compareBy(collator) { it.label })
            }
    }

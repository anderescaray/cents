package io.github.anderescaray.cents.ui.capture

import android.content.Context
import android.content.Intent
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
    /** Every app with an icon in the launcher, except Cents itself, sorted by name. */
    suspend fun launchable(): List<InstalledApp>
}

/**
 * Asks the package manager for the launcher's apps.
 *
 * **Launcher apps only**, because those are the ones the owner recognises by name: a bank's app
 * always has an icon, while the hundreds of system packages behind them never post a payment.
 * Since Android 11 the question is only answered for what the manifest's `<queries>` declares,
 * and that block asks for exactly this intent rather than for every package on the phone.
 */
class PackageManagerInstalledApps
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : InstalledApps {
        override suspend fun launchable(): List<InstalledApp> =
            withContext(Dispatchers.IO) {
                val manager = context.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val collator = Collator.getInstance()
                manager
                    .queryIntentActivities(launcher, 0)
                    .map { it.activityInfo.applicationInfo }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != context.packageName }
                    .map { InstalledApp(it.packageName, manager.getApplicationLabel(it).toString()) }
                    .sortedWith(compareBy(collator) { it.label })
            }
    }

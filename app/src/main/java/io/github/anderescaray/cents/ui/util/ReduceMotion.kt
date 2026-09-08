package io.github.anderescaray.cents.ui.util

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the device has been told to stop animating things.
 *
 * Android has no single "reduce motion" switch: turning animations off in Developer options and
 * *Remove animations* in Accessibility both land on the same place, an animator duration scale
 * of zero. Anything that moves on its own — a drift nobody asked for, most of all — has to read
 * this, because a moving target is harder to hit for anyone with a motor impairment and makes
 * some people ill.
 *
 * Read once per composition rather than observed: the setting changing while the app is open is
 * rare, and the next launch picks it up.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.animationsAreOff() }
}

private fun Context.animationsAreOff(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

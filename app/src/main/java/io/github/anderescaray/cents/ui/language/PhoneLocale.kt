package io.github.anderescaray.cents.ui.language

import android.app.LocaleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * The phone's own locale, whatever language the app is shown in: what names its currency, and how
 * amounts and dates are written (`DECISIONS.md` D36).
 *
 * From Android 13 a per-app language is part of the process's configuration, which the system's
 * resources take on too, so only [LocaleManager] still tells the two apart. Before 13 the app's
 * language never reaches the system's resources.
 *
 * **Read once, and again after the phone's language changes.** Every amount on screen asks for it,
 * and from 13 the read is a call into the system. A change is learnt from the app's configuration,
 * which arrives before a recreated screen is drawn, and from 13 also from
 * [Intent.ACTION_LOCALE_CHANGED], because there a language of the app's own can leave the
 * configuration unchanged when only the phone's moved. Before 13 the configuration is the phone's.
 */
object PhoneLocale {
    /** The application, kept from [onAppStart] so [get] can ask the system. */
    @Volatile private var application: Context? = null

    /**
     * The current reading. A change puts an empty one in its place rather than clearing it, so a
     * read that began before the change fills the old reading, which nothing asks again.
     */
    @Volatile private var reading = Reading()

    private val changes =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                reading = Reading()
            }
        }

    /** Run once when the process starts, before anything is drawn. */
    fun onAppStart(context: Context) {
        val app = context.applicationContext
        application = app
        reading = Reading()
        if (AppLanguages.appliedBySystem) {
            // Not exported: a system broadcast reaches a receiver that is not.
            app.registerReceiver(changes, IntentFilter(Intent.ACTION_LOCALE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        }
    }

    /** Run when the app's configuration changes, which may be the phone's language changing. */
    fun onConfigurationChanged() {
        reading = Reading()
    }

    fun get(): Locale {
        val current = reading
        return current.locale ?: read().also { current.locale = it }
    }

    private fun read(): Locale {
        val context = application
        val locales =
            if (AppLanguages.appliedBySystem && context != null) {
                context.getSystemService(LocaleManager::class.java).systemLocales
            } else {
                Resources.getSystem().configuration.locales
            }
        return locales.firstOrNull() ?: Locale.getDefault()
    }

    private class Reading {
        @Volatile var locale: Locale? = null
    }
}

internal fun LocaleList.firstOrNull(): Locale? = if (isEmpty) null else get(0)

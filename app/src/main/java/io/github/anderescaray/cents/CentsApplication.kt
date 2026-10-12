package io.github.anderescaray.cents

import android.app.Application
import android.content.res.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.anderescaray.cents.ui.language.AppLanguages
import io.github.anderescaray.cents.ui.language.PhoneLocale

/**
 * Application entry point. Annotating it with [HiltAndroidApp] is what generates the
 * dependency graph and makes injection available to every activity, view model and worker.
 *
 * **Nothing is seeded here.** A fresh install has no accounts and no categories: everything
 * in the app is something the owner typed (`DECISIONS.md` D1, and PRD §4.1 design principle
 * 3 for the categories). The movement form leads to creating an account when there are none,
 * which is what makes an empty database a first run rather than a dead end.
 */
@HiltAndroidApp
class CentsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The language chosen in Settings, and the phone's, which amounts and dates are written in.
        AppLanguages.onAppStart(this)
        PhoneLocale.onAppStart(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguages.onConfigurationChanged(this)
        PhoneLocale.onConfigurationChanged()
    }
}

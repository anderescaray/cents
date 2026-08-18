package io.github.anderescaray.cents

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Annotating it with [HiltAndroidApp] is what generates the
 * dependency graph and makes injection available to every activity, view model and worker.
 */
@HiltAndroidApp
class CentsApplication : Application()

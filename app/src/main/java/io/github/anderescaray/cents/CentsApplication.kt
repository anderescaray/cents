package io.github.anderescaray.cents

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.anderescaray.cents.data.repository.DefaultDataSeeder
import io.github.anderescaray.cents.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Annotating it with [HiltAndroidApp] is what generates the
 * dependency graph and makes injection available to every activity, view model and worker.
 */
@HiltAndroidApp
class CentsApplication : Application() {
    @Inject
    lateinit var seeder: DefaultDataSeeder

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // First run only: gives the entry screen something to pick from.
        applicationScope.launch { seeder.seedIfEmpty() }
    }
}

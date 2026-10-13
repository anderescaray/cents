package io.github.anderescaray.cents.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.ui.capture.InstalledApps
import io.github.anderescaray.cents.ui.capture.ListenerNotifyingApps
import io.github.anderescaray.cents.ui.capture.NotifyingApps
import io.github.anderescaray.cents.ui.capture.PackageManagerInstalledApps

/** What capture's settings need from the phone itself, rather than from storage. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {
    @Binds
    abstract fun bindInstalledApps(impl: PackageManagerInstalledApps): InstalledApps

    @Binds
    abstract fun bindNotifyingApps(impl: ListenerNotifyingApps): NotifyingApps
}

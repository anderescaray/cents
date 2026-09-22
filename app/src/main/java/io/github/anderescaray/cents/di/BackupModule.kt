package io.github.anderescaray.cents.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.data.backup.DefaultDriveBackupStorage
import io.github.anderescaray.cents.data.backup.DriveClient
import io.github.anderescaray.cents.data.backup.DriveFiles
import io.github.anderescaray.cents.data.local.preferences.DataStoreDriveBackupRepository
import io.github.anderescaray.cents.domain.repository.DriveBackupRepository
import io.github.anderescaray.cents.domain.repository.DriveBackupStorage
import io.github.anderescaray.cents.ui.backup.DriveAuthorization
import io.github.anderescaray.cents.ui.backup.DriveBackupScheduler
import io.github.anderescaray.cents.ui.backup.PlayServicesDriveAuthorization
import io.github.anderescaray.cents.ui.backup.WorkManagerDriveBackupScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    abstract fun bindDriveBackupRepository(impl: DataStoreDriveBackupRepository): DriveBackupRepository

    @Binds
    abstract fun bindDriveBackupStorage(impl: DefaultDriveBackupStorage): DriveBackupStorage

    @Binds
    abstract fun bindDriveAuthorization(impl: PlayServicesDriveAuthorization): DriveAuthorization

    @Binds
    abstract fun bindDriveBackupScheduler(impl: WorkManagerDriveBackupScheduler): DriveBackupScheduler

    companion object {
        @Provides
        @Singleton
        fun provideDriveFiles(): DriveFiles =
            DriveClient(
                OkHttpClient
                    .Builder()
                    .retryOnConnectionFailure(false)
                    .followRedirects(false)
                    .callTimeout(1, TimeUnit.MINUTES)
                    .build(),
            )
    }
}

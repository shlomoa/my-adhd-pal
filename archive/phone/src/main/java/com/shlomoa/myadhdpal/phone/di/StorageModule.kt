package com.shlomoa.myadhdpal.phone.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.shlomoa.myadhdpal.phone.data.DefaultLinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.DefaultPromptRepository
import com.shlomoa.myadhdpal.phone.data.DefaultScheduleRepository
import com.shlomoa.myadhdpal.phone.data.DefaultWatchStatusRepository
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.LinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.PromptRepository
import com.shlomoa.myadhdpal.phone.data.PublishedPrompts
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.data.ScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.WatchStatusRepository
import com.shlomoa.myadhdpal.phone.data.datastore.DataStorePublishedPrompts
import com.shlomoa.myadhdpal.phone.data.datastore.DataStoreScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.datastore.DataStoreSettingsRepository
import com.shlomoa.myadhdpal.phone.data.db.AppDatabase
import com.shlomoa.myadhdpal.phone.data.db.RoomHistoryRepository
import com.shlomoa.myadhdpal.phone.data.db.RoomTaskRepository
import com.shlomoa.myadhdpal.phone.link.PromptCleaner
import com.shlomoa.myadhdpal.phone.link.WatchEventProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScheduleStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PromptStore

/** Storage and the repositories the UI builds on: Room for tasks and history, DataStore for settings and versions. */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase = AppDatabase.create(context)

    @Provides
    @Singleton
    @SettingsStore
    fun settingsStore(@ApplicationContext context: Context, @AppScope scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    @ScheduleStore
    fun scheduleStore(@ApplicationContext context: Context, @AppScope scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("schedule") }

    @Provides
    @Singleton
    @PromptStore
    fun promptStore(@ApplicationContext context: Context, @AppScope scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("prompts") }

    @Provides
    @Singleton
    fun publishedPrompts(@PromptStore store: DataStore<Preferences>): PublishedPrompts =
        DataStorePublishedPrompts(store)

    @Provides
    @Singleton
    fun tasks(db: AppDatabase): TaskRepository = RoomTaskRepository(db)

    @Provides
    @Singleton
    fun history(db: AppDatabase): HistoryRepository = RoomHistoryRepository(db)

    @Provides
    @Singleton
    fun settings(@SettingsStore store: DataStore<Preferences>): SettingsRepository = DataStoreSettingsRepository(store)

    @Provides
    @Singleton
    fun versionStore(@ScheduleStore store: DataStore<Preferences>): ScheduleVersionStore =
        DataStoreScheduleVersionStore(store)
}

/** The repositories that need the watch link, and the processor that stores what the watch sends. */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun schedules(
        tasks: TaskRepository,
        settings: SettingsRepository,
        source: WatchLinkDataSource,
        store: ScheduleVersionStore
    ): ScheduleRepository = DefaultScheduleRepository(tasks, settings, source, store)

    @Provides
    @Singleton
    fun prompts(source: WatchLinkDataSource, published: PublishedPrompts, clock: Clock): PromptRepository =
        DefaultPromptRepository(source, published, clock = { clock.instant() })

    @Provides
    @Singleton
    fun linkStatus(source: WatchLinkDataSource): LinkStatusRepository = DefaultLinkStatusRepository(source)

    @Provides
    @Singleton
    fun watchStatus(source: WatchLinkDataSource): WatchStatusRepository = DefaultWatchStatusRepository(source)

    @Provides
    @Singleton
    fun eventProcessor(
        @ApplicationContext context: Context,
        source: WatchLinkDataSource,
        history: HistoryRepository,
        published: PublishedPrompts,
        clock: Clock
    ): WatchEventProcessor {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
        val cleaner = PromptCleaner(source, published) { clock.instant() }
        return WatchEventProcessor(source, history, version ?: "0", cleaner)
    }
}

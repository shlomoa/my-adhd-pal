package com.shlomoa.myadhdpal.phone.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.shlomoa.myadhdpal.phone.alert.AlertPreferences
import com.shlomoa.myadhdpal.phone.alert.AlertRaiser
import com.shlomoa.myadhdpal.phone.alert.AndroidAlertNotifier
import com.shlomoa.myadhdpal.phone.alert.DataStoreAlertPreferences
import com.shlomoa.myadhdpal.phone.alert.DataStoreRaisedAlerts
import com.shlomoa.myadhdpal.phone.alert.RaisedAlertsStore
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AlertStore

/** The phone alert: its own small DataStore file (outside `data/`), the notifier and the raiser. */
@Module
@InstallIn(SingletonComponent::class)
object AlertModule {
    @Provides
    @Singleton
    @AlertStore
    fun alertStore(@ApplicationContext context: Context, @AppScope scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("alert") }

    @Provides
    @Singleton
    fun alertPreferences(@AlertStore store: DataStore<Preferences>): DataStoreAlertPreferences =
        DataStoreAlertPreferences(store)

    @Provides
    fun alertPreferencesInterface(preferences: DataStoreAlertPreferences): AlertPreferences = preferences

    @Provides
    @Singleton
    fun raisedAlerts(@AlertStore store: DataStore<Preferences>): RaisedAlertsStore = DataStoreRaisedAlerts(store)

    @Provides
    @Singleton
    fun alertNotifier(@ApplicationContext context: Context): AndroidAlertNotifier = AndroidAlertNotifier(context)

    @Provides
    @Singleton
    fun alertRaiser(
        history: HistoryRepository,
        raised: RaisedAlertsStore,
        notifier: AndroidAlertNotifier,
        preferences: DataStoreAlertPreferences,
        source: WatchLinkDataSource
    ): AlertRaiser = AlertRaiser(
        history,
        raised,
        notifier,
        prefsProvider = { preferences.prefs.first() },
        hasAmplitudeControl = notifier::hasAmplitudeControl,
        endEpisode = source::publishEpisodeEnd
    )
}

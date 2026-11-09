package com.shlomoa.myadhdpal.phone

import android.app.Application
import com.shlomoa.myadhdpal.phone.alert.AlertRaiser
import com.shlomoa.myadhdpal.phone.di.AppScope
import com.shlomoa.myadhdpal.phone.link.DataLayerWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.link.WatchEventProcessor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class MyAdhdPalApp : Application() {
    @Inject
    lateinit var linkSource: DataLayerWatchLinkDataSource

    @Inject
    lateinit var processor: WatchEventProcessor

    @Inject
    lateinit var alertRaiser: AlertRaiser

    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Items sent while the phone was away are read, stored and cleaned up; the link state is kept fresh while the
        // UI watches it. The same happens in a process the listener service started with the app closed.
        processor.start(appScope)
        linkSource.start(appScope)
        // The phone raises its own alert for every alert the watch sends, also in a process that the listener
        // service started with the app closed.
        appScope.launch { alertRaiser.run() }
    }
}

package com.shlomoa.myadhdpal.phone

import android.app.Application
import com.shlomoa.myadhdpal.phone.link.DataLayerWatchLinkDataSource
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class MyAdhdPalApp : Application() {
    @Inject
    lateinit var linkSource: DataLayerWatchLinkDataSource

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Reads items sent while the phone was away and keeps the link state fresh while the UI watches it.
        linkSource.start(appScope)
    }
}

package com.shlomoa.myadhdpal.phone.link

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Declared in the manifest, so the system starts the app's process for a Data Layer event even when the app is closed
 * (hardware, `docs/protocol.md`: delivery within 1 to 4 s after a kill, force-stop, Doze and the `rare` and
 * `restricted` buckets). Hands the changed items to the data source. `onDataChanged` runs on a background thread, so
 * the work is finished before the service can be stopped.
 */
@AndroidEntryPoint
class PhoneLinkListenerService : WearableListenerService() {
    @Inject
    lateinit var source: DataLayerWatchLinkDataSource

    override fun onDataChanged(events: DataEventBuffer) {
        // The buffer is released when this method returns, so copy what is needed first.
        val items = events
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .mapNotNull { it.dataItem.toRemoteItem() }
        if (items.isNotEmpty()) runBlocking { source.ingest(items) }
    }
}

package com.shlomoa.myadhdpal.wear.datalayer

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.log.Logger

/**
 * Started by the system when the phone changes a `/schedule`, `/prompt` or `/hello/phone` item, also when the app is
 * not running. It hands each event to the transport, whose listeners apply a schedule, re-arm the alarms and so on.
 */
class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val transport = (application as WatchApplication).graph.dataLayer().transport
        for (event in events) {
            val item = event.dataItem
            val path = item.uri.path.orEmpty()
            val origin = item.uri.host.orEmpty()
            if (event.type == DataEvent.TYPE_DELETED) {
                transport.onDataDeleted(path, origin)
                continue
            }
            val envelope = envelopeOf(item.toFieldMap())
            if (envelope == null) Logger.w(TAG, "item at $path dropped: missing or mistyped key") else {
                transport.onDataChanged(path, envelope, origin)
            }
        }
    }

    private companion object {
        const val TAG = "WatchListener"
    }
}

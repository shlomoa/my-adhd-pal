package com.shlomoa.myadhdpal.wear.spike

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/** THROWAWAY spike code. Declared in the manifest, so the system starts the app's process to deliver events. */
class SpikeListenerService : WearableListenerService() {
    override fun onCreate() {
        super.onCreate()
        Spike.log(this, "SERVICE-CREATED")
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            val path = event.dataItem.uri.path.orEmpty()
            if (event.type == DataEvent.TYPE_DELETED) {
                Spike.log(this, "EVENT-DELETED path=$path host=${event.dataItem.uri.host}")
                continue
            }
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val from = map.getString("from")
            if (from == Spike.ROLE) {
                Spike.log(this, "EVENT-OWN-WRITE ignored path=$path")
                continue
            }
            val recvTs = System.currentTimeMillis()
            val sentTs = map.getLong("sentTs")
            Spike.log(this, "RECV path=$path from=$from sentTs=$sentTs recvTs=$recvTs delayMs=${recvTs - sentTs}")
            if (map.getString("kind") == "del") {
                Wearable.getDataClient(this).deleteDataItems(event.dataItem.uri)
                    .addOnSuccessListener { Spike.log(this, "DELETE-OK path=$path count=$it") }
                    .addOnFailureListener { Spike.log(this, "DELETE-FAILED path=$path error=$it") }
            }
        }
    }
}

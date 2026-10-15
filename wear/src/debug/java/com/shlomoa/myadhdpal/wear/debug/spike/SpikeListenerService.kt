package com.shlomoa.myadhdpal.wear.debug.spike

import android.os.SystemClock
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/** Spike code. Declared in the manifest, so the system starts the app's process to deliver events. */
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
            when (map.getString("kind")) {
                "del" -> {
                    val delayMs = map.getLong("deleteDelayMs")
                    if (delayMs > 0) {
                        // Deletion is time-stamped; deleting some seconds later separates "delete lost to clock skew" from other causes.
                        Spike.log(this, "DELETE-SCHEDULED path=$path afterMs=$delayMs")
                        val uri = event.dataItem.uri
                        val appContext = applicationContext
                        Thread {
                            Thread.sleep(delayMs)
                            delete(appContext, uri, path)
                        }.start()
                    } else {
                        delete(applicationContext, event.dataItem.uri, path)
                    }
                }

                "ping" -> {
                    // Reply with a pong that echoes the pinger's elapsed-realtime, then consume the ping.
                    Spike.pong(
                        this,
                        map.getString("id").orEmpty(),
                        map.getBoolean("urgent"),
                        map.getLong("pingElapsed")
                    )
                    delete(applicationContext, event.dataItem.uri, path)
                }

                "pong" -> {
                    val rtt = SystemClock.elapsedRealtime() - map.getLong("pingElapsed")
                    Spike.log(this, "RTT id=${map.getString("id")} urgent=${map.getBoolean("urgent")} rttMs=$rtt")
                    delete(applicationContext, event.dataItem.uri, path)
                }
            }
        }
    }

    private fun delete(context: android.content.Context, uri: android.net.Uri, path: String) {
        Wearable.getDataClient(context).deleteDataItems(uri)
            .addOnSuccessListener { Spike.log(context, "DELETE-OK path=$path count=$it") }
            .addOnFailureListener { Spike.log(context, "DELETE-FAILED path=$path error=$it") }
    }
}

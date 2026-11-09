package com.shlomoa.myadhdpal.poc.phone

import android.os.SystemClock
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.shlomoa.myadhdpal.poc.core.Paths
import com.shlomoa.myadhdpal.poc.core.Wire

/** Data Layer path: receives readings and pings from the watch. The arrival time is taken first in the callback. */
class ReceiverService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val arrival = SystemClock.elapsedRealtimeNanos()
        when (event.path) {
            Paths.PING -> {
                val t0 = Wire.decodePing(event.data) ?: return
                val pong = Wire.encodePong(t0, SystemClock.elapsedRealtimeNanos())
                Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, Paths.PONG, pong)
            }

            Paths.SAMPLE -> {
                val sample = Wire.decodeSample(event.data) ?: return
                PhoneState.record(this, "dl", sample, arrival)
            }
        }
    }
}

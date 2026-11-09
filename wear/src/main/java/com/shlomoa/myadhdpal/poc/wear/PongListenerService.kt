package com.shlomoa.myadhdpal.poc.wear

import android.os.SystemClock
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.shlomoa.myadhdpal.poc.core.Paths
import com.shlomoa.myadhdpal.poc.core.Wire

/** Receives the phone's answers to the watch's pings and feeds the clock-offset estimate. */
class PongListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != Paths.PONG) return
        val t3 = SystemClock.elapsedRealtimeNanos()
        val (t0, phoneNanos) = Wire.decodePong(event.data) ?: return
        Link.onPong(t0, phoneNanos, t3)
    }
}

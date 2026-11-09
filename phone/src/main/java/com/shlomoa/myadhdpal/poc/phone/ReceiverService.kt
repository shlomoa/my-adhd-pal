package com.shlomoa.myadhdpal.poc.phone

import android.os.SystemClock
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.shlomoa.myadhdpal.poc.core.Paths
import com.shlomoa.myadhdpal.poc.core.Wire

/** Receives readings and pings from the watch. The arrival time is taken first thing in the callback. */
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
                val s = Wire.decodeSample(event.data) ?: return
                PhoneState.received++
                PhoneState.lastText = "${s.bpm.toInt()} bpm (${s.label} #${s.seq})"
                // label,seq,bpm,sourceNanos,sendNanos,offsetNanos,phoneArrivalNanos
                PhoneState.append(
                    this,
                    "phone-${s.label}.csv",
                    "${s.label},${s.seq},${s.bpm},${s.sourceNanos},${s.sendNanos},${s.offsetNanos},$arrival",
                )
            }
        }
    }

}

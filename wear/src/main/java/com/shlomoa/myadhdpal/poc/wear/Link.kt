package com.shlomoa.myadhdpal.poc.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.shlomoa.myadhdpal.poc.core.Exchange
import com.shlomoa.myadhdpal.poc.core.OffsetTracker
import com.shlomoa.myadhdpal.poc.core.Sample

/**
 * Watch side of the Wearable Data Layer path to the phone: message sending and this path's clock offset.
 * The Data Layer prefers Bluetooth but may use Wi-Fi, so it is compared with the direct Bluetooth path ([BtLink]).
 */
object Link {
    private val tracker = OffsetTracker()

    val offsetNanos: Long get() = tracker.offset()

    @Volatile
    var nodeFound: Boolean = false
        private set

    @Volatile
    private var nodeId: String? = null

    fun refreshNode(context: Context) {
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            nodeId = node?.id
            nodeFound = node != null
        }
    }

    /** Sends one message; false when no phone node is known yet (a refresh is started). */
    fun send(context: Context, path: String, bytes: ByteArray): Boolean {
        val id = nodeId
        if (id == null) {
            refreshNode(context)
            return false
        }
        Wearable.getMessageClient(context).sendMessage(id, path, bytes)
        return true
    }

    fun onPong(t0: Long, phoneNanos: Long, t3: Long) = tracker.add(Exchange(t0, phoneNanos, t3))

    fun reset() = tracker.clear()

    fun noOffset(): Boolean = tracker.offset() == Sample.NO_OFFSET
}

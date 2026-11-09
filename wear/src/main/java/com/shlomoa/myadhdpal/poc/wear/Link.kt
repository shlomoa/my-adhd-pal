package com.shlomoa.myadhdpal.poc.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.shlomoa.myadhdpal.poc.core.ClockOffset
import com.shlomoa.myadhdpal.poc.core.Exchange
import com.shlomoa.myadhdpal.poc.core.Sample

/** Watch side of the link to the phone: message sending over the Wearable Data Layer and the clock offset. */
object Link {
    private val exchanges = ArrayDeque<Exchange>()

    @Volatile
    var offsetNanos: Long = Sample.NO_OFFSET
        private set

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

    @Synchronized
    fun onPong(t0: Long, phoneNanos: Long, t3: Long) {
        exchanges.addLast(Exchange(t0, phoneNanos, t3))
        while (exchanges.size > MAX_EXCHANGES) exchanges.removeFirst()
        ClockOffset.best(exchanges.toList())?.let { offsetNanos = it }
    }

    @Synchronized
    fun reset() {
        exchanges.clear()
        offsetNanos = Sample.NO_OFFSET
    }

    private const val MAX_EXCHANGES = 10
}

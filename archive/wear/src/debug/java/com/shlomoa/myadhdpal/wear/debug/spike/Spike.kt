package com.shlomoa.myadhdpal.wear.debug.spike

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.util.UUID

/** Hardware spike code (docs/spikes/README.md). Debug builds only, never in a release build. */
object Spike {
    const val ROLE = "watch"
    const val TAG = "SPIKE"

    /** Command actions for [SpikeCommandReceiver]: adb shell am broadcast -n <component> -a <action> [extras]. */
    const val ACTION_SEND = "com.shlomoa.myadhdpal.spike.SEND"
    const val ACTION_PING = "com.shlomoa.myadhdpal.spike.PING"
    const val ACTION_LIST_ITEMS = "com.shlomoa.myadhdpal.spike.LIST_ITEMS"
    const val ACTION_LIST_NODES = "com.shlomoa.myadhdpal.spike.LIST_NODES"
    const val ACTION_CLEAR_LOG = "com.shlomoa.myadhdpal.spike.CLEAR_LOG"

    @Volatile var foreground = false
    private val lock = Any()

    fun log(context: Context, message: String) {
        val upMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val head = "${System.currentTimeMillis()} role=$ROLE pid=${Process.myPid()}"
        val line = "$head processUptimeMs=$upMs fg=$foreground $message"
        Log.i(TAG, line)
        synchronized(lock) { File(context.filesDir, "spike.log").appendText(line + "\n") }
    }

    fun readLog(context: Context): String = synchronized(lock) {
        File(context.filesDir, "spike.log").takeIf { it.exists() }?.readText().orEmpty()
    }

    fun clearLog(context: Context) = synchronized(lock) { File(context.filesDir, "spike.log").delete() }

    /** kind "del": the receiver deletes the item after reading it. kind "keep": the receiver leaves it. */
    fun send(context: Context, kind: String, urgent: Boolean, onDone: () -> Unit = {}, deleteDelayMs: Long = 0) {
        val id = UUID.randomUUID().toString()
        val path = "/spike/$kind/$id"
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString("from", ROLE)
            dataMap.putString("id", id)
            dataMap.putString("kind", kind)
            dataMap.putLong("sentTs", System.currentTimeMillis())
            dataMap.putLong("deleteDelayMs", deleteDelayMs)
        }.asPutDataRequest()
        if (urgent) request.setUrgent()
        log(context, "SEND path=$path urgent=$urgent")
        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener { log(context, "SEND-OK path=$path (putDataItem completed locally)") }
            .addOnFailureListener { log(context, "SEND-FAILED path=$path error=$it") }
            .addOnCompleteListener { onDone() }
    }

    /**
     * Round trip on THIS device's clock: write /spike/ping/<id> with the local elapsed-realtime, the peer replies with
     * /spike/pong/<id> echoing it, and the listener logs RTT = now - echoed value. No cross-device clock is involved.
     */
    fun ping(context: Context, urgent: Boolean, onDone: () -> Unit = {}) {
        val id = UUID.randomUUID().toString()
        val path = "/spike/ping/$id"
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString("from", ROLE)
            dataMap.putString("id", id)
            dataMap.putString("kind", "ping")
            dataMap.putBoolean("urgent", urgent)
            dataMap.putLong("sentTs", System.currentTimeMillis())
            dataMap.putLong("pingElapsed", SystemClock.elapsedRealtime())
        }.asPutDataRequest()
        if (urgent) request.setUrgent()
        log(context, "PING path=$path urgent=$urgent")
        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener { log(context, "PING-OK path=$path (putDataItem completed locally)") }
            .addOnFailureListener { log(context, "PING-FAILED path=$path error=$it") }
            .addOnCompleteListener { onDone() }
    }

    fun pong(context: Context, id: String, urgent: Boolean, pingElapsed: Long) {
        val path = "/spike/pong/$id"
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString("from", ROLE)
            dataMap.putString("id", id)
            dataMap.putString("kind", "pong")
            dataMap.putBoolean("urgent", urgent)
            dataMap.putLong("sentTs", System.currentTimeMillis())
            dataMap.putLong("pingElapsed", pingElapsed)
        }.asPutDataRequest()
        if (urgent) request.setUrgent()
        log(context, "PONG path=$path urgent=$urgent")
        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener { log(context, "PONG-OK path=$path") }
            .addOnFailureListener { log(context, "PONG-FAILED path=$path error=$it") }
    }

    fun listItems(context: Context, onDone: () -> Unit = {}) {
        Wearable.getDataClient(context).dataItems
            .addOnSuccessListener { buffer ->
                log(context, "ITEMS count=${buffer.count} " + buffer.joinToString { it.uri.toString() })
                buffer.release()
            }
            .addOnFailureListener { log(context, "ITEMS-FAILED error=$it") }
            .addOnCompleteListener { onDone() }
    }

    fun listNodes(context: Context, onDone: () -> Unit = {}) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                log(
                    context,
                    "NODES count=${nodes.size} " + nodes.joinToString { "${it.displayName}(nearby=${it.isNearby})" }
                )
            }
            .addOnFailureListener { log(context, "NODES-FAILED error=$it") }
            .addOnCompleteListener { onDone() }
    }
}

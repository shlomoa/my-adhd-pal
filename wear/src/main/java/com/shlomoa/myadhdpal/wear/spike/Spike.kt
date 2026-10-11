package com.shlomoa.myadhdpal.wear.spike

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.util.UUID

/** THROWAWAY spike code (plan.md 0b.2a). Never merged to main. */
object Spike {
    const val ROLE = "watch"
    const val TAG = "SPIKE"

    @Volatile var foreground = false
    private val lock = Any()

    fun log(context: Context, message: String) {
        val upMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val line = "${System.currentTimeMillis()} role=$ROLE pid=${Process.myPid()} processUptimeMs=$upMs fg=$foreground $message"
        Log.i(TAG, line)
        synchronized(lock) { File(context.filesDir, "spike.log").appendText(line + "\n") }
    }

    fun readLog(context: Context): String = synchronized(lock) {
        File(context.filesDir, "spike.log").takeIf { it.exists() }?.readText().orEmpty()
    }

    fun clearLog(context: Context) = synchronized(lock) { File(context.filesDir, "spike.log").delete() }

    /** kind "del": the receiver deletes the item after reading it. kind "keep": the receiver leaves it. */
    fun send(context: Context, kind: String, urgent: Boolean) {
        val id = UUID.randomUUID().toString()
        val path = "/spike/$kind/$id"
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString("from", ROLE)
            dataMap.putString("id", id)
            dataMap.putString("kind", kind)
            dataMap.putLong("sentTs", System.currentTimeMillis())
        }.asPutDataRequest()
        if (urgent) request.setUrgent()
        log(context, "SEND path=$path urgent=$urgent")
        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener { log(context, "SEND-OK path=$path (putDataItem completed locally)") }
            .addOnFailureListener { log(context, "SEND-FAILED path=$path error=$it") }
    }

    fun listItems(context: Context) {
        Wearable.getDataClient(context).dataItems
            .addOnSuccessListener { buffer ->
                log(context, "ITEMS count=${buffer.count} " + buffer.joinToString { it.uri.toString() })
                buffer.release()
            }
            .addOnFailureListener { log(context, "ITEMS-FAILED error=$it") }
    }

    fun listNodes(context: Context) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                log(context, "NODES count=${nodes.size} " + nodes.joinToString { "${it.displayName}(nearby=${it.isNearby})" })
            }
            .addOnFailureListener { log(context, "NODES-FAILED error=$it") }
    }
}

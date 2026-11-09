package com.shlomoa.myadhdpal.phone.link

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.ItemKeys
import com.shlomoa.myadhdpal.protocol.NodeObservation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [DataLayerClient] over the Google Play services Wearable API. Items are `DataMap`s with the four keys of
 * [ItemKeys] (`v` int, `id` string, `ts` long, `body` string); the same mapping is used by the watch app.
 * Only the connected device runs this code in production; there is no unit test against the real service, the
 * logic above it is tested with a fake and the whole path by the hardware demo.
 */
class GmsDataLayerClient(context: Context) : DataLayerClient {
    private val appContext = context.applicationContext
    private val data get() = Wearable.getDataClient(appContext)
    private val nodes get() = Wearable.getNodeClient(appContext)
    private val capabilities get() = Wearable.getCapabilityClient(appContext)

    @Volatile
    private var cachedLocalNode: String? = null

    override suspend fun localNode(): String =
        cachedLocalNode ?: nodes.localNode.await().id.also { cachedLocalNode = it }

    override suspend fun allItems(): List<RemoteItem> {
        val buffer = data.dataItems.await()
        try {
            return buffer.mapNotNull { it.toRemoteItem() }
        } finally {
            buffer.release()
        }
    }

    override suspend fun put(path: String, envelope: Envelope, urgent: Boolean) {
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putInt(ItemKeys.VERSION, envelope.version)
            dataMap.putString(ItemKeys.ID, envelope.id)
            dataMap.putLong(ItemKeys.TIMESTAMP, envelope.timestamp)
            dataMap.putString(ItemKeys.BODY, envelope.body)
        }.asPutDataRequest()
        if (urgent) request.setUrgent()
        data.putDataItem(request).await()
    }

    override suspend fun delete(nodeId: String, path: String) {
        data.deleteDataItems(Uri.Builder().scheme("wear").authority(nodeId).path(path).build()).await()
    }

    override suspend fun connectedNodes(): List<NodeObservation> =
        nodes.connectedNodes.await().map { it.toObservation() }

    override suspend fun nodesWithCapability(name: String): List<NodeObservation> =
        capabilities.getCapability(name, CapabilityClient.FILTER_REACHABLE).await().nodes.map { it.toObservation() }

    private fun Node.toObservation() = NodeObservation(id, isNearby)
}

/** Suspends until the Task completes. Kept here so the app needs no extra library for it. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

/** The item as a [RemoteItem], or null when it lacks the body key (not one of ours). */
internal fun DataItem.toRemoteItem(): RemoteItem? {
    val map = DataMapItem.fromDataItem(this).dataMap
    val body = map.getString(ItemKeys.BODY) ?: return null
    val envelope = Envelope(
        map.getInt(ItemKeys.VERSION),
        map.getString(ItemKeys.ID).orEmpty(),
        map.getLong(ItemKeys.TIMESTAMP),
        body
    )
    return RemoteItem(uri.host.orEmpty(), uri.path.orEmpty(), envelope)
}

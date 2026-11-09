package com.shlomoa.myadhdpal.wear.datalayer

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.shlomoa.myadhdpal.protocol.Capabilities
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.ItemKeys
import com.shlomoa.myadhdpal.protocol.LinkObservation
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkStateLogic
import com.shlomoa.myadhdpal.protocol.NodeObservation
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.wear.log.Logger

/**
 * The watch end of the link over the Wearable Data Layer (DataItems only): a [CachedTransport] whose writes go to the
 * Data Layer. [start] reads the stored items once and the local node id, [WatchListenerService] feeds the Data Layer's
 * events in, and [refreshLinkState] observes the nodes and the phone app capability (the Data Layer has no callback for
 * that, so the app calls it about every 2 s while a screen shows the state). On the watch [LinkState.NO_WATCH_APP]
 * means no phone app and the screens show it as Away. Not unit-tested (Android types); the mapping and the cache are.
 */
class DataLayerTransport(context: Context) {
    private val app = context.applicationContext
    private val data = Wearable.getDataClient(app)

    @Volatile
    private var observed: LinkState = LinkState.AWAY

    @Volatile
    private var localNodeId: String = ""

    val transport = CachedTransport(Writer(), localNodeId = { localNodeId }, observeLinkState = { observed })

    /** Reads the local node id and the items already stored (then calls [onLoaded]), then observes the link. */
    fun start(onLoaded: () -> Unit = {}) {
        Wearable.getNodeClient(app).localNode.addOnSuccessListener { localNodeId = it.id }
        data.dataItems.addOnSuccessListener { buffer ->
            val items = buffer.mapNotNull { item ->
                val envelope = envelopeOf(item.toFieldMap()) ?: return@mapNotNull null
                StoredItem(item.uri.path.orEmpty(), envelope)
            }
            buffer.release()
            transport.load(items)
            onLoaded()
        }.addOnFailureListener { Logger.w(TAG, "reading the stored items failed: $it") }
        refreshLinkState()
    }

    /** Observes connected nodes and phone-app nodes, then the phone's hello version, and reports a changed state. */
    fun refreshLinkState() {
        Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { connected ->
            Wearable.getCapabilityClient(app)
                .getCapability(Capabilities.PHONE_APP, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { capability ->
                    observed = LinkStateLogic.derive(
                        LinkObservation(connected.map(::observe), capability.nodes.map(::observe), phoneHelloVersion())
                    )
                    transport.refreshLinkState()
                }
        }.addOnFailureListener { Logger.w(TAG, "reading the nodes failed: $it") }
    }

    private fun observe(node: Node) = NodeObservation(node.id, node.isNearby)

    /** The phone's protocol version, "" when its hello cannot be decoded, null when there is none yet. */
    private fun phoneHelloVersion(): String? {
        val hello = transport.items().firstOrNull { it.path == Paths.HELLO_PHONE } ?: return null
        val decoded = Codec.decode(hello.path, hello.envelope)
        return ((decoded as? DecodeResult.Ok)?.item?.message as? Hello)?.protocolVersion.orEmpty()
    }

    private inner class Writer : ItemWriter {
        override fun put(path: String, envelope: Envelope) {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putInt(ItemKeys.VERSION, envelope.version)
                dataMap.putString(ItemKeys.ID, envelope.id)
                dataMap.putLong(ItemKeys.TIMESTAMP, envelope.timestamp)
                dataMap.putString(ItemKeys.BODY, envelope.body)
            }.asPutDataRequest()
            if (isUrgent(path)) request.setUrgent()
            data.putDataItem(request).addOnFailureListener { Logger.w(TAG, "put $path failed: $it") }
        }

        override fun delete(path: String) {
            val node = localNodeId
            if (node.isEmpty()) {
                Logger.w(TAG, "delete $path skipped: local node not known yet")
                return
            }
            val uri = Uri.Builder().scheme("wear").authority(node).path(path).build()
            data.deleteDataItems(uri).addOnFailureListener { Logger.w(TAG, "delete $path failed: $it") }
        }
    }

    private companion object {
        const val TAG = "DataLayer"
    }
}

/** The four keys of a stored `DataItem` with the types [toFields] writes; an absent key stays out of the map. */
internal fun DataItem.toFieldMap(): Map<String, Any?> {
    val map = DataMapItem.fromDataItem(this).dataMap
    return listOf(ItemKeys.VERSION, ItemKeys.ID, ItemKeys.TIMESTAMP, ItemKeys.BODY)
        .mapNotNull { key -> map.get<Any?>(key)?.let { key to it } }
        .toMap()
}

package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.NodeObservation

/** A DataItem as stored on a node: [nodeId] is the host of its URI (the writer), then its path and its four keys. */
data class RemoteItem(val nodeId: String, val path: String, val envelope: Envelope)

/**
 * The few Wearable Data Layer calls the phone needs, as suspend functions without any Android type. The real
 * implementation is [GmsDataLayerClient]; tests use a fake. Calls may throw; the callers report that as a failure.
 */
interface DataLayerClient {
    /** The node id of this phone. */
    suspend fun localNode(): String

    /** Every DataItem currently stored, from every node. */
    suspend fun allItems(): List<RemoteItem>

    /** Writes the item at [path] on this node, urgent or not. */
    suspend fun put(path: String, envelope: Envelope, urgent: Boolean)

    /** Deletes the item at [path] written by [nodeId]: this node's own, or the watch's after it was stored. */
    suspend fun delete(nodeId: String, path: String)

    /** The nodes of `NodeClient.getConnectedNodes()`. */
    suspend fun connectedNodes(): List<NodeObservation>

    /** The reachable nodes that declare the capability [name]. */
    suspend fun nodesWithCapability(name: String): List<NodeObservation>
}

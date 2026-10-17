package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.NodeObservation

/** Test double for [DataLayerClient]: records writes and deletes, serves the node lists the test sets. */
class FakeDataLayerClient(val localNodeId: String = "phone-node") : DataLayerClient {
    data class Put(val path: String, val envelope: Envelope, val urgent: Boolean)

    var items: List<RemoteItem> = emptyList()
    var connected: List<NodeObservation> = emptyList()
    var withCapability: Map<String, List<NodeObservation>> = emptyMap()
    var failPutWith: String? = null
    val puts = mutableListOf<Put>()
    val deletes = mutableListOf<Pair<String, String>>()

    override suspend fun localNode(): String = localNodeId

    override suspend fun allItems(): List<RemoteItem> = items

    override suspend fun put(path: String, envelope: Envelope, urgent: Boolean) {
        failPutWith?.let { throw IllegalStateException(it) }
        puts += Put(path, envelope, urgent)
    }

    override suspend fun delete(nodeId: String, path: String) {
        deletes += nodeId to path
    }

    override suspend fun connectedNodes(): List<NodeObservation> = connected

    override suspend fun nodesWithCapability(name: String): List<NodeObservation> = withCapability[name].orEmpty()
}

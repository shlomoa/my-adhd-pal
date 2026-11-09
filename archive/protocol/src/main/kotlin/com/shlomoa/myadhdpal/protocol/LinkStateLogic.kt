package com.shlomoa.myadhdpal.protocol

/** Data Layer capability names. Each app declares its own in an `android_wear_capabilities` resource. */
object Capabilities {
    const val WATCH_APP = "adhd_pal_watch"
    const val PHONE_APP = "adhd_pal_phone"
}

/** A node as the Data Layer reports it. [isNearby] is true for a direct link and false when the link is relayed. */
data class NodeObservation(val id: String, val isNearby: Boolean)

/**
 * What one side observed, in Data Layer terms but without any Android type. The phone looks for the watch app
 * ([Capabilities.WATCH_APP]) and reads the watch's hello; the watch looks for [Capabilities.PHONE_APP] and reads the
 * phone's hello, and shows [LinkState.NO_WATCH_APP] as Away:
 * - [connected]: the nodes of `NodeClient.getConnectedNodes()`, with or without the peer app;
 * - [withApp]: the reachable nodes that declare the peer's capability (`CapabilityClient`, reachable filter);
 * - [peerHelloVersion]: the `protocolVersion` of the peer's hello item. `null` means no hello has arrived yet.
 *   Text that cannot be parsed is malformed, including the empty text the data source passes for a hello item it
 *   could not decode.
 */
data class LinkObservation(
    val connected: List<NodeObservation>,
    val withApp: List<NodeObservation>,
    val peerHelloVersion: String?
)

/** Step 1.5: the five link states of `docs/protocol.md` from the Data Layer observations. Pure, no I/O, no clock. */
object LinkStateLogic {
    /**
     * Precedence, first match wins:
     * 1. no connected node and none with the app: [LinkState.AWAY];
     * 2. no node with the app: [LinkState.NO_WATCH_APP] (a watch is connected but cannot be the other end of the link);
     * 3. the hello version is present and not compatible: [LinkState.INCOMPATIBLE];
     * 4. a node with the app is nearby: [LinkState.NEARBY];
     * 5. otherwise [LinkState.RELAYED].
     *
     * A node with the app that the node list does not show (the two queries are not atomic) is still a reachable
     * watch, so [LinkObservation.withApp] counts on its own. A missing hello does not hide a watch whose capability is
     * present: the watch app writes its hello at start, and the check applies once it arrives.
     */
    fun derive(observation: LinkObservation): LinkState = when {
        observation.connected.isEmpty() && observation.withApp.isEmpty() -> LinkState.AWAY
        observation.withApp.isEmpty() -> LinkState.NO_WATCH_APP
        isIncompatible(observation.peerHelloVersion) -> LinkState.INCOMPATIBLE
        observation.withApp.any { it.isNearby } -> LinkState.NEARBY
        else -> LinkState.RELAYED
    }

    private fun isIncompatible(helloVersion: String?): Boolean =
        helloVersion != null && Compatibility.ofHello(helloVersion) is Compatibility.Incompatible
}

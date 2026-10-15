package com.shlomoa.myadhdpal.protocol

/** Data Layer capability names. The watch app declares [WATCH_APP] in an `android_wear_capabilities` resource. */
object Capabilities {
    const val WATCH_APP = "adhd_pal_watch"
}

/** A node as the Data Layer reports it. [isNearby] is true for a direct link and false when the link is relayed. */
data class NodeObservation(val id: String, val isNearby: Boolean)

/**
 * What the phone observed, in Data Layer terms but without any Android type:
 * - [connected]: the nodes of `NodeClient.getConnectedNodes()`, with or without the app;
 * - [withApp]: the reachable nodes that declare [Capabilities.WATCH_APP] (`CapabilityClient`, reachable filter);
 * - [watchHelloVersion]: the `protocolVersion` of the watch's hello item. `null` means no hello has arrived yet.
 *   Text that cannot be parsed is malformed, including the empty text the data source passes for a hello item it
 *   could not decode.
 */
data class LinkObservation(
    val connected: List<NodeObservation>,
    val withApp: List<NodeObservation>,
    val watchHelloVersion: String?
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
        isIncompatible(observation.watchHelloVersion) -> LinkState.INCOMPATIBLE
        observation.withApp.any { it.isNearby } -> LinkState.NEARBY
        else -> LinkState.RELAYED
    }

    private fun isIncompatible(helloVersion: String?): Boolean =
        helloVersion != null && Compatibility.ofHello(helloVersion) is Compatibility.Incompatible
}

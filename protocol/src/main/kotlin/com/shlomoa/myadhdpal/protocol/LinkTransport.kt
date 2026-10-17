package com.shlomoa.myadhdpal.protocol

/**
 * The state of the watch-phone link as shown to the user. The phone shows all five; the watch shows Nearby, Relayed and
 * Away. The function that derives it from the Data Layer observations is step 1.5; consumers only read it from
 * [LinkTransport.linkState].
 */
enum class LinkState { NEARBY, RELAYED, AWAY, NO_WATCH_APP, INCOMPATIBLE }

/** An item stored on a node: the path and the four keys of the item. */
data class StoredItem(val path: String, val envelope: Envelope)

/** Callbacks of a [LinkTransport]. They run on the thread that changed the data (the fake runs them synchronously). */
interface TransportListener {
    /** An item written by the other node was added or replaced. */
    fun onItemChanged(item: StoredItem)

    /** An item was deleted by the other node. */
    fun onItemDeleted(path: String)

    fun onLinkStateChanged(state: LinkState)
}

/**
 * One end of the watch-phone link, with DataItem semantics (`docs/protocol.md`, item catalog): [put] stores the item
 * locally at once and the other node receives it when the link allows; a newer item at the same path replaces the older
 * one; a deletion reaches the other node the same way. The real implementations wrap the Wearable Data Layer in `:wear`
 * and `:phone`; [com.shlomoa.myadhdpal.protocol.fake.FakeLink] is the test double. A `MessageClient` for urgent alerts,
 * if the hardware spike requires it, is added behind this interface without changing any path, key or model.
 */
interface LinkTransport {
    val linkState: LinkState

    fun put(path: String, envelope: Envelope)

    fun delete(path: String)

    /** The items currently stored on this node (its own writes and the other node's). */
    fun items(): List<StoredItem>

    fun setListener(listener: TransportListener?)
}

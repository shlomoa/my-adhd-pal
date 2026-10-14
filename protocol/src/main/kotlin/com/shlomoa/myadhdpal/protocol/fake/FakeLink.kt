package com.shlomoa.myadhdpal.protocol.fake

import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.TransportListener

/**
 * Two [FakeNode]s joined by a link that can be connected and disconnected, with the buffering of DataItems: an item
 * written while the link is down is delivered, in order, when it comes back; a newer item at the same path replaces the
 * older queued one; an item deleted before it was delivered is never delivered. Everything is synchronous and
 * deterministic. Starts disconnected ([LinkState.AWAY]).
 */
class FakeLink {
    val phone = FakeNode(this)
    val watch = FakeNode(this)

    var state: LinkState = LinkState.AWAY
        private set

    init {
        phone.peer = watch
        watch.peer = phone
    }

    fun connect(nearby: Boolean) = setState(if (nearby) LinkState.NEARBY else LinkState.RELAYED)

    fun disconnect() = setState(LinkState.AWAY)

    /** Sets the state both nodes report. Items flow in Nearby, Relayed and Incompatible; they queue otherwise. */
    fun setState(newState: LinkState) {
        if (newState == state) return
        state = newState
        phone.listener?.onLinkStateChanged(newState)
        watch.listener?.onLinkStateChanged(newState)
        phone.flush()
        watch.flush()
    }

    internal val flowing: Boolean
        get() = state == LinkState.NEARBY || state == LinkState.RELAYED || state == LinkState.INCOMPATIBLE
}

class FakeNode internal constructor(private val link: FakeLink) : LinkTransport {
    internal lateinit var peer: FakeNode
    internal var listener: TransportListener? = null

    private val stored = LinkedHashMap<String, Envelope>()
    private val outbox = ArrayList<Operation>()

    private sealed interface Operation {
        val path: String

        data class Put(override val path: String, val envelope: Envelope) : Operation

        data class Delete(override val path: String) : Operation
    }

    override val linkState: LinkState
        get() = link.state

    override fun put(path: String, envelope: Envelope) {
        stored[path] = envelope
        outbox.removeAll { it.path == path }
        outbox.add(Operation.Put(path, envelope))
        flush()
    }

    override fun delete(path: String) {
        stored.remove(path)
        outbox.removeAll { it.path == path }
        if (peer.stored.containsKey(path)) outbox.add(Operation.Delete(path))
        flush()
    }

    override fun items(): List<StoredItem> = stored.map { StoredItem(it.key, it.value) }

    override fun setListener(listener: TransportListener?) {
        this.listener = listener
    }

    internal fun flush() {
        while (link.flowing && outbox.isNotEmpty()) {
            when (val operation = outbox.removeAt(0)) {
                is Operation.Put -> {
                    peer.stored[operation.path] = operation.envelope
                    peer.listener?.onItemChanged(StoredItem(operation.path, operation.envelope))
                }

                is Operation.Delete -> {
                    peer.stored.remove(operation.path)
                    peer.listener?.onItemDeleted(operation.path)
                }
            }
        }
    }
}

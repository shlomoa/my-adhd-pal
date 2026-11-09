package com.shlomoa.myadhdpal.wear.datalayer

import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.TransportListener

/** Asynchronous writes to the Data Layer. Failures are the writer's to log; the cache already holds the change. */
interface ItemWriter {
    fun put(path: String, envelope: Envelope)

    fun delete(path: String)
}

/**
 * [LinkTransport] over an in-memory cache, so the synchronous interface never blocks on the Data Layer. [put] and
 * [delete] change the cache at once and hand the write to [writer]; the Data Layer's events arrive through
 * [onDataChanged] and [onDataDeleted] and are reported to the listener only when the other node wrote them. [load]
 * fills the cache once at start. All methods may be called from different threads.
 */
class CachedTransport(
    private val writer: ItemWriter,
    private val localNodeId: () -> String,
    private val observeLinkState: () -> LinkState
) : LinkTransport {
    private val lock = Any()
    private val cache = LinkedHashMap<String, Envelope>()
    private val touched = HashSet<String>()
    private var listener: TransportListener? = null
    private var reportedState: LinkState = observeLinkState()

    override val linkState: LinkState
        get() = observeLinkState()

    /** Adds the stored items that no change, local or from the Data Layer, has touched since the cache was created. */
    fun load(items: List<StoredItem>) = synchronized(lock) {
        for (item in items) if (item.path !in touched) cache[item.path] = item.envelope
    }

    override fun put(path: String, envelope: Envelope) {
        synchronized(lock) {
            cache[path] = envelope
            touched += path
        }
        writer.put(path, envelope)
    }

    override fun delete(path: String) {
        synchronized(lock) {
            cache.remove(path)
            touched += path
        }
        writer.delete(path)
    }

    override fun items(): List<StoredItem> = synchronized(lock) { cache.map { StoredItem(it.key, it.value) } }

    override fun setListener(listener: TransportListener?) = synchronized(lock) { this.listener = listener }

    /** An item was added or replaced in the Data Layer by the node [originNodeId]. */
    fun onDataChanged(path: String, envelope: Envelope, originNodeId: String) {
        val target = synchronized(lock) {
            cache[path] = envelope
            touched += path
            listener.takeIf { originNodeId != localNodeId() }
        }
        target?.onItemChanged(StoredItem(path, envelope))
    }

    /** An item was deleted in the Data Layer by the node [originNodeId]. */
    fun onDataDeleted(path: String, originNodeId: String) {
        val target = synchronized(lock) {
            cache.remove(path)
            touched += path
            listener.takeIf { originNodeId != localNodeId() }
        }
        target?.onItemDeleted(path)
    }

    /** Re-reads the link state; the Data Layer has no callback for it, so the app calls this about every 2 s. */
    fun refreshLinkState() {
        val state = observeLinkState()
        val target = synchronized(lock) {
            if (state == reportedState) null else listener.also { reportedState = state }
        }
        target?.onLinkStateChanged(state)
    }
}

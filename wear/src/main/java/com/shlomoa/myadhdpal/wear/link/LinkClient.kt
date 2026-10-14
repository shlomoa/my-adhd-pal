package com.shlomoa.myadhdpal.wear.link

import androidx.annotation.StringRes
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Side
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.TransportListener
import com.shlomoa.myadhdpal.wear.R
import java.time.Instant

/** What the home screen and the tile show about the link: the state and when the schedule was last synced. */
data class LinkView(val state: LinkState, val lastSyncAt: Instant?)

/**
 * The watch end of the link status: publishes `/hello/watch` at start and keeps a [LinkView] in step with the
 * transport. The state is read from [LinkTransport.linkState] (derived in the transport, step 1.5) on every callback
 * and on [refresh], which the app calls periodically because the Data Layer has no connect or disconnect callback.
 * Register the client as the transport's listener (or from a fan-out that includes it).
 */
class LinkClient(
    private val transport: LinkTransport,
    private val appVersion: String,
    private val newId: () -> String,
    private val now: () -> Instant,
    lastSyncAt: Instant?
) : TransportListener {
    private var onChange: ((LinkView) -> Unit)? = null

    var view: LinkView = LinkView(transport.linkState, lastSyncAt)
        private set

    fun setOnChange(callback: ((LinkView) -> Unit)?) {
        onChange = callback
    }

    /** Overwrites `/hello/watch` with the protocol and app version and reads the current state. */
    fun start() {
        val hello = Hello(Side.WATCH, Protocol.VERSION, appVersion)
        transport.put(Paths.HELLO_WATCH, Codec.encode(newId(), now().toEpochMilli(), hello))
        refresh()
    }

    fun refresh() = update(view.copy(state = transport.linkState))

    fun onScheduleApplied(at: Instant) = update(view.copy(lastSyncAt = at))

    override fun onLinkStateChanged(state: LinkState) = update(view.copy(state = state))

    override fun onItemChanged(item: StoredItem) = Unit

    override fun onItemDeleted(path: String) = Unit

    private fun update(newView: LinkView) {
        if (newView == view) return
        view = newView
        onChange?.invoke(newView)
    }
}

/**
 * The label for a state on the watch, which shows Nearby, Relayed, Away and Incompatible. A watch never sees
 * [LinkState.NO_WATCH_APP] (that is the phone's view of a missing watch app), so it reads as Away.
 */
@StringRes
fun LinkState.watchLabelRes(): Int = when (this) {
    LinkState.NEARBY -> R.string.link_nearby
    LinkState.RELAYED -> R.string.link_relayed
    LinkState.AWAY, LinkState.NO_WATCH_APP -> R.string.link_away
    LinkState.INCOMPATIBLE -> R.string.link_incompatible
}

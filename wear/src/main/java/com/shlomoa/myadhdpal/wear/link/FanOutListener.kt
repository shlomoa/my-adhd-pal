package com.shlomoa.myadhdpal.wear.link

import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.TransportListener

/** A transport has one listener; this passes every callback to several consumers. */
class FanOutListener(private vararg val listeners: TransportListener) : TransportListener {
    override fun onItemChanged(item: StoredItem) = listeners.forEach { it.onItemChanged(item) }

    override fun onItemDeleted(path: String) = listeners.forEach { it.onItemDeleted(path) }

    override fun onLinkStateChanged(state: LinkState) = listeners.forEach { it.onLinkStateChanged(state) }
}

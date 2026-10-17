package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.Compatibility
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.ItemPath
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Selection
import com.shlomoa.myadhdpal.protocol.Side
import com.shlomoa.myadhdpal.protocol.WatchStatus

/** What one watch-written item became: an [event] (null when dropped) and, for a hello, the version it carried. */
data class Decoded(
    val event: WatchEvent?,
    val helloVersion: String? = null,
    val isHello: Boolean = false,
    val status: WatchStatus? = null,
    val statusAt: Long = 0L
)

/** Pure decoding of watch-written items into [WatchEvent]s; anything not valid and compatible is dropped. */
object WatchItemDecoder {
    /** Returns null for paths the phone does not read (its own paths, unknown paths). */
    fun decode(item: RemoteItem): Decoded? = when (val path = Paths.parse(item.path)) {
        is ItemPath.Ack, is ItemPath.Selection, is ItemPath.Alert -> Decoded(event(item))
        is ItemPath.Hello -> if (path.side == Side.WATCH) hello(item) else null
        ItemPath.WatchStatus -> status(item)
        else -> null
    }

    private fun event(item: RemoteItem): WatchEvent? {
        val decoded = Codec.decodeIfCompatible(item.path, item.envelope) as? DecodeResult.Ok ?: return null
        val id = decoded.item.id
        val at = decoded.item.timestamp
        return when (val message = decoded.item.message) {
            is Acknowledgement -> WatchEvent.AckReceived(id, at, message)
            is Selection -> WatchEvent.SelectionReceived(id, at, message)
            is Alert -> WatchEvent.AlertReceived(id, at, message)
            else -> null
        }
    }

    /** The watch status is state: [Decoded.status] is set, there is no event, and nothing is ever deleted. */
    private fun status(item: RemoteItem): Decoded {
        val decoded = Codec.decodeIfCompatible(item.path, item.envelope) as? DecodeResult.Ok
        val status = decoded?.item?.message as? WatchStatus
        return Decoded(null, status = status, statusAt = decoded?.item?.timestamp ?: 0L)
    }

    /**
     * A hello of another major version carries a version the link-state function must see as Incompatible, so it is
     * reported as `<major>.0` without reading the body; a body that cannot be decoded is reported as empty text, which
     * is malformed and so Incompatible too.
     */
    private fun hello(item: RemoteItem): Decoded {
        val envelope = item.envelope
        if (Compatibility.ofMajor(envelope.version) is Compatibility.Incompatible) {
            return Decoded(null, "${envelope.version}.0", isHello = true)
        }
        val decoded = Codec.decode(item.path, envelope) as? DecodeResult.Ok
        val hello = decoded?.item?.message as? Hello
        val event = hello?.let { WatchEvent.HelloReceived(decoded.item.id, decoded.item.timestamp, it) }
        return Decoded(event, hello?.protocolVersion ?: "", isHello = true)
    }
}

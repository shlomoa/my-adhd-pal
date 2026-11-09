package com.shlomoa.myadhdpal.wear.prompt

import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.ItemPath
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.Selection
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.TransportListener
import com.shlomoa.myadhdpal.wear.log.Logger
import com.shlomoa.myadhdpal.wear.reminder.isExpiredAt
import java.time.Instant

data class PendingPrompt(val id: String, val prompt: Prompt)

/**
 * The prompts waiting for an answer, derived from the items on the node, so nothing is kept that could disagree with
 * them: a prompt is pending if it is a valid, supported `/prompt/<id>` item that has not expired on the watch clock and
 * has no `/selection/<id>` item of the watch's own yet. The phone writes prompts and deletes them (writer owns
 * cleanup); the watch only writes selections. Register the inbox as the transport's listener (or in a fan-out).
 */
class PromptInbox(private val transport: LinkTransport, private val now: () -> Instant) : TransportListener {
    private var onChange: (() -> Unit)? = null

    fun setOnChange(callback: (() -> Unit)?) {
        onChange = callback
    }

    /** Checks the prompts already on the node when the process starts, so that invalid ones are logged once. */
    fun start() {
        transport.items().filter { Paths.parse(it.path) is ItemPath.Prompt }.forEach { decode(it) }
    }

    /** Pending prompts, the one that arrived first at the front. */
    fun pending(): List<PendingPrompt> {
        val items = transport.items()
        val answered = items.mapTo(HashSet()) { it.path }
        val clock = now().toEpochMilli()
        return items
            .mapNotNull { stored -> decode(stored, log = false)?.let { stored to it } }
            .filter { (stored, pending) ->
                !pending.prompt.isExpiredAt(clock) && Paths.selection(pending.id) !in answered &&
                    stored.path.isNotEmpty()
            }
            .sortedWith(compareBy({ it.first.envelope.timestamp }, { it.second.id }))
            .map { it.second }
    }

    /** Sends selection [key] for prompt [id]; false, with nothing sent, if it is not pending or has no such option. */
    fun choose(id: String, key: String): Boolean {
        val pending = pending().firstOrNull { it.id == id }
        val option = pending?.prompt?.options?.firstOrNull { it.key == key }
        if (option == null) return false
        val at = now().toEpochMilli()
        transport.put(Paths.selection(id), Codec.encode(id, at, Selection(option.key, at)))
        onChange?.invoke()
        return true
    }

    override fun onItemChanged(item: StoredItem) {
        if (Paths.parse(item.path) !is ItemPath.Prompt) return
        decode(item)
        onChange?.invoke()
    }

    override fun onItemDeleted(path: String) {
        if (Paths.parse(path) is ItemPath.Prompt) onChange?.invoke()
    }

    override fun onLinkStateChanged(state: LinkState) = Unit

    private fun decode(stored: StoredItem, log: Boolean = true): PendingPrompt? {
        val kind = Paths.parse(stored.path) as? ItemPath.Prompt ?: return null
        return when (val decoded = Codec.decodeIfCompatible(stored.path, stored.envelope)) {
            is DecodeResult.Ok -> (decoded.item.message as? Prompt)?.let { PendingPrompt(kind.id, it) }

            is DecodeResult.Failure -> {
                if (log) Logger.w(TAG, "prompt item dropped: ${decoded.reason}")
                null
            }
        }
    }

    private companion object {
        const val TAG = "PromptInbox"
    }
}

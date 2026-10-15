package com.shlomoa.myadhdpal.wear.datalayer

import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.ItemKeys
import com.shlomoa.myadhdpal.protocol.ItemPath
import com.shlomoa.myadhdpal.protocol.Paths

/** The four keys of an item as plain values, which `DataMap` stores as int, string, long and string. */
fun Envelope.toFields(): Map<String, Any> = mapOf(
    ItemKeys.VERSION to version,
    ItemKeys.ID to id,
    ItemKeys.TIMESTAMP to timestamp,
    ItemKeys.BODY to body
)

/** Null when a key is missing or has the wrong type: such an item is dropped. */
fun envelopeOf(fields: Map<String, Any?>): Envelope? =
    (fields[ItemKeys.VERSION] as? Int)?.let { version ->
        (fields[ItemKeys.ID] as? String)?.let { id ->
            (fields[ItemKeys.TIMESTAMP] as? Long)?.let { timestamp ->
                (fields[ItemKeys.BODY] as? String)?.let { body -> Envelope(version, id, timestamp, body) }
            }
        }
    }

/** Items the user waits for are sent as urgent (`docs/protocol.md`, item catalog); hello and schedule items are not. */
fun isUrgent(path: String): Boolean = when (Paths.parse(path)) {
    is ItemPath.Prompt, is ItemPath.Ack, is ItemPath.Selection, is ItemPath.Alert -> true
    else -> false
}

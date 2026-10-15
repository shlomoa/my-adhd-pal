package com.shlomoa.myadhdpal.wear.datalayer

import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.ItemKeys

/** The four keys of an item as plain values, which `DataMap` stores as int, string, long and string. */
fun Envelope.toFields(): Map<String, Any> = mapOf(
    ItemKeys.VERSION to version,
    ItemKeys.ID to id,
    ItemKeys.TIMESTAMP to timestamp,
    ItemKeys.BODY to body
)

/** Null when a key is missing or has the wrong type: such an item is dropped. */
fun envelopeOf(fields: Map<String, Any?>): Envelope? {
    val version = fields[ItemKeys.VERSION] as? Int
    val id = fields[ItemKeys.ID] as? String
    val timestamp = fields[ItemKeys.TIMESTAMP] as? Long
    val body = fields[ItemKeys.BODY] as? String
    return if (version == null || id == null || timestamp == null || body == null) {
        null
    } else {
        Envelope(version, id, timestamp, body)
    }
}

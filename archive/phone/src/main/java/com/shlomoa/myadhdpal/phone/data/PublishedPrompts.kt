package com.shlomoa.myadhdpal.phone.data

import java.time.Instant

/** A prompt the phone wrote to the Data Layer and still has to remove; [expiresAt] is null when it never expires. */
data class PublishedPrompt(val id: String, val expiresAt: Instant?)

/**
 * The prompts the phone has published and not yet removed (writer-owns-cleanup, issue 198). A prompt is added when it
 * is sent and forgotten when the phone removes it, on the answer or at its expiry.
 */
interface PublishedPrompts {
    suspend fun add(id: String, expiresAt: Instant?)

    suspend fun remove(id: String)

    suspend fun all(): List<PublishedPrompt>
}

package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.PublishedPrompts
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import java.time.Instant

/**
 * Removes the prompts the phone wrote (writer-owns-cleanup, issue 198): when the answer for a prompt has been stored,
 * and when a prompt has expired. The answer to a prompt is the `/ack/<id>` or `/selection/<id>` item with the id of
 * the prompt; any other event is not an answer. A prompt that was removed is forgotten, so nothing is removed twice.
 */
class PromptCleaner(
    private val source: WatchLinkDataSource,
    private val prompts: PublishedPrompts,
    private val clock: () -> Instant
) {
    suspend fun answered(event: WatchEvent) {
        if (event !is WatchEvent.AckReceived && event !is WatchEvent.SelectionReceived) return
        if (prompts.all().any { it.id == event.id }) remove(event.id)
    }

    suspend fun sweepExpired() {
        val now = clock()
        prompts.all().filter { it.expiresAt != null && !it.expiresAt.isAfter(now) }.forEach { remove(it.id) }
    }

    private suspend fun remove(id: String) {
        source.removePrompt(id)
        prompts.remove(id)
    }
}

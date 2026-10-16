package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.protocol.ItemPath
import com.shlomoa.myadhdpal.protocol.Paths
import java.time.Clock

/** Writer owns cleanup: the phone deletes its own `/episode-end/<id>` items once they are older than one hour. */
class EpisodeEndCleaner(private val client: DataLayerClient, private val clock: Clock) {
    suspend fun deleteOld() {
        val local = client.localNode()
        val cutoff = clock.millis() - KEEP_MILLIS
        client.allItems()
            .filter {
                it.nodeId == local && Paths.parse(it.path) is ItemPath.EpisodeEnd &&
                    it.envelope.timestamp < cutoff
            }
            .forEach { client.delete(local, it.path) }
    }

    private companion object {
        const val KEEP_MILLIS = 3_600_000L
    }
}

/** The watch-written items that were turned into events and are not yet stored, by event id. */
class PendingItems {
    private val items = mutableMapOf<String, RemoteItem>()

    /** True when the event id was not pending before (so the event is new), false for a duplicate. */
    @Synchronized
    fun offer(id: String, item: RemoteItem): Boolean = items.putIfAbsent(id, item) == null

    @Synchronized
    fun remove(id: String): RemoteItem? = items.remove(id)
}

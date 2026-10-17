package com.shlomoa.myadhdpal.phone.data

import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.Selection
import com.shlomoa.myadhdpal.protocol.WatchStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * An item from the watch, already decoded and validated (a valid, compatible item only; anything else is dropped by
 * the data source). [id] is the item id, [sentAt] the watch's `ts` in epoch milliseconds.
 */
sealed interface WatchEvent {
    val id: String
    val sentAt: Long

    data class AckReceived(override val id: String, override val sentAt: Long, val ack: Acknowledgement) : WatchEvent

    data class SelectionReceived(override val id: String, override val sentAt: Long, val selection: Selection) :
        WatchEvent

    data class AlertReceived(override val id: String, override val sentAt: Long, val alert: Alert) : WatchEvent

    data class HelloReceived(override val id: String, override val sentAt: Long, val hello: Hello) : WatchEvent
}

/** The outcome of handing an item to the Data Layer. [Sent] means accepted for delivery, not delivered. */
sealed interface PublishResult {
    data object Sent : PublishResult

    data class Failed(val reason: String) : PublishResult
}

/**
 * The phone's only door to the watch. The real implementation (step 2.4) wraps a `LinkTransport` and the Wearable Data
 * Layer; tests and ViewModels use `FakeWatchLinkDataSource`. Nothing outside `data` and `di` touches the Data Layer.
 */
interface WatchLinkDataSource {
    /** The link state of `docs/protocol.md`, as an output; the real source computes it (step 1.5). */
    val linkState: StateFlow<LinkState>

    /** When an item from the watch last arrived, or null if none has. */
    val lastSync: StateFlow<Instant?>

    /**
     * The last `/status/watch` item (protocol 1.2): baseline, threshold and heart-rate permission, or null until the
     * watch has reported. It is state, not an event: the watch overwrites the item and the phone never deletes it, so
     * it survives a restart (the source reads it again at start).
     */
    val watchStatus: StateFlow<WatchStatus?>

    /** Items from the watch, each delivered once to the single collector, in order, until [markStored] is called. */
    val events: Flow<WatchEvent>

    suspend fun publishSchedule(schedule: Schedule): PublishResult

    suspend fun publishPrompt(id: String, prompt: Prompt): PublishResult

    suspend fun publishHello(hello: Hello): PublishResult

    /**
     * Tells the watch the user answered "I'm OK" for the alert episode [episodeId] (protocol 1.2). The source builds
     * the `EpisodeEnd` with its clock and a fresh item id and writes it urgently; the watch ends that episode or
     * ignores it. The phone's own `/episode-end/<id>` items older than one hour are deleted when one is written and
     * once at start.
     */
    suspend fun publishEpisodeEnd(episodeId: String): PublishResult

    /** Deletes the phone's own prompt item (the answer arrived or it expired; writer owns cleanup). */
    suspend fun removePrompt(id: String)

    /** Tells the source the event is stored, so it deletes the watch-written item. Never call before the row exists. */
    suspend fun markStored(event: WatchEvent)
}

package com.shlomoa.myadhdpal.phone.data.fake

import com.shlomoa.myadhdpal.phone.data.PublishResult
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.Schedule
import java.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Test double for [WatchLinkDataSource]. It starts disconnected (Away). Events emitted while the link is Away or there
 * is No watch app are held back and delivered, in order, when the link comes up (like DataItems). Everything the app
 * publishes is recorded; [failNextPublish] makes the next publish fail once.
 */
class FakeWatchLinkDataSource(private val now: () -> Instant = { Instant.now() }) : WatchLinkDataSource {
    private val state = MutableStateFlow(LinkState.AWAY)
    private val sync = MutableStateFlow<Instant?>(null)
    private val channel = Channel<WatchEvent>(Channel.UNLIMITED)
    private val held = ArrayList<WatchEvent>()
    private var nextFailure: String? = null

    override val linkState: StateFlow<LinkState> = state
    override val lastSync: StateFlow<Instant?> = sync
    override val events: Flow<WatchEvent> = channel.receiveAsFlow()

    val publishedSchedules = mutableListOf<Schedule>()
    val publishedPrompts = mutableListOf<Pair<String, Prompt>>()
    val publishedHellos = mutableListOf<Hello>()
    val removedPrompts = mutableListOf<String>()
    val storedIds = mutableListOf<String>()

    /** Makes the item come from the watch. */
    fun emit(event: WatchEvent) {
        if (flowing()) deliver(event) else held += event
    }

    fun connect(nearby: Boolean) = setLinkState(if (nearby) LinkState.NEARBY else LinkState.RELAYED)

    fun disconnect() = setLinkState(LinkState.AWAY)

    fun setLinkState(newState: LinkState) {
        state.value = newState
        if (flowing()) {
            val pending = held.toList()
            held.clear()
            pending.forEach { deliver(it) }
        }
    }

    fun failNextPublish(reason: String) {
        nextFailure = reason
    }

    override suspend fun publishSchedule(schedule: Schedule): PublishResult = publish { publishedSchedules += schedule }

    override suspend fun publishPrompt(id: String, prompt: Prompt): PublishResult =
        publish { publishedPrompts += id to prompt }

    override suspend fun publishHello(hello: Hello): PublishResult = publish { publishedHellos += hello }

    override suspend fun removePrompt(id: String) {
        removedPrompts += id
    }

    override suspend fun markStored(event: WatchEvent) {
        storedIds += event.id
    }

    private fun flowing() = state.value != LinkState.AWAY && state.value != LinkState.NO_WATCH_APP

    private fun deliver(event: WatchEvent) {
        channel.trySend(event)
        sync.value = now()
    }

    private inline fun publish(record: () -> Unit): PublishResult {
        val failure = nextFailure
        if (failure != null) {
            nextFailure = null
            return PublishResult.Failed(failure)
        }
        record()
        return PublishResult.Sent
    }
}

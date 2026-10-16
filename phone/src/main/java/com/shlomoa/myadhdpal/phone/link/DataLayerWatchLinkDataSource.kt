package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.PublishResult
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.protocol.Capabilities
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.LinkObservation
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkStateLogic
import com.shlomoa.myadhdpal.protocol.Message
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.WatchStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The phone's real [WatchLinkDataSource] over the Wearable Data Layer (step 2.4). Items the watch wrote reach
 * [ingest] from the listener service, from [scanExisting] at start, or both; each becomes one [WatchEvent] until
 * [markStored] (writer-owns-cleanup: the phone deletes watch-written events after storing them, never before). The
 * link state is recomputed by [refreshLinkState] about every [refreshMillis] while [start] runs, because the Data Layer
 * has no documented callback for node changes (hardware, `docs/protocol.md`). Decoding is [WatchItemDecoder].
 */
class DataLayerWatchLinkDataSource(
    private val client: DataLayerClient,
    private val clock: Clock,
    private val refreshMillis: Long = DEFAULT_REFRESH_MILLIS
) : WatchLinkDataSource {
    private val state = MutableStateFlow(LinkState.AWAY)
    private val sync = MutableStateFlow<Instant?>(null)
    private val channel = Channel<WatchEvent>(Channel.UNLIMITED)
    private val pending = mutableMapOf<String, RemoteItem>()
    private val lock = Any()
    private var watchHelloVersion: String? = null
    private var lastSchedule: Schedule? = null

    override val linkState: StateFlow<LinkState> = state
    override val lastSync: StateFlow<Instant?> = sync
    private val latestStatus = LatestStatus()
    override val watchStatus: StateFlow<WatchStatus?> = latestStatus.state
    override val events: Flow<WatchEvent> = channel.receiveAsFlow()

    /**
     * Reads what is already stored (items sent while the phone was away) and refreshes the link state once; after
     * that the state is refreshed every [refreshMillis] only while something collects [linkState], so a process woken
     * in the background by the listener service does not poll.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            attempt { scanExisting() }
            attempt { refreshLinkState() }
            state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { watched ->
                while (watched) {
                    attempt { refreshLinkState() }
                    delay(refreshMillis)
                }
            }
        }
    }

    suspend fun scanExisting() {
        ingest(client.allItems().sortedBy { it.envelope.timestamp })
    }

    /** Turns watch-written items into events. Everything else (own writes, unknown paths, bad bodies) is dropped. */
    suspend fun ingest(items: List<RemoteItem>) {
        val local = client.localNode()
        for (item in items.filter { it.nodeId != local }) {
            val decoded = WatchItemDecoder.decode(item) ?: continue
            if (decoded.isHello) watchHelloVersion = decoded.helloVersion
            decoded.status?.let { if (latestStatus.accept(it, decoded.statusAt)) sync.value = clock.instant() }
            decoded.event?.let { deliver(it, item) }
        }
    }

    private fun deliver(event: WatchEvent, item: RemoteItem) {
        val isNew = synchronized(lock) { pending.putIfAbsent(event.id, item) == null }
        if (!isNew) return
        channel.trySend(event)
        sync.value = clock.instant()
    }

    suspend fun refreshLinkState() {
        val observation = LinkObservation(
            connected = client.connectedNodes(),
            withApp = client.nodesWithCapability(Capabilities.WATCH_APP),
            peerHelloVersion = watchHelloVersion
        )
        state.value = LinkStateLogic.derive(observation)
    }

    override suspend fun publishSchedule(schedule: Schedule): PublishResult {
        val urgent = ScheduleUrgency.changesNextHalfHour(lastSchedule, schedule, clock.instant(), clock.zone)
        val result = put(Paths.SCHEDULE, UUID.randomUUID().toString(), schedule, urgent)
        if (result == PublishResult.Sent) lastSchedule = schedule
        return result
    }

    override suspend fun publishPrompt(id: String, prompt: Prompt): PublishResult =
        put(Paths.prompt(id), id, prompt, urgent = true)

    override suspend fun publishHello(hello: Hello): PublishResult {
        val id = UUID.randomUUID().toString()
        return put(Paths.pathFor(id, hello), id, hello, urgent = false)
    }

    override suspend fun removePrompt(id: String) {
        attempt { client.delete(client.localNode(), Paths.prompt(id)) }
    }

    override suspend fun markStored(event: WatchEvent) {
        val item = synchronized(lock) { pending.remove(event.id) } ?: return
        if (event is WatchEvent.HelloReceived) return
        // If the delete fails the item stays in the Data Layer and is handled again by id (idempotent), not lost.
        attempt { client.delete(item.nodeId, item.path) }
    }

    private suspend fun put(path: String, id: String, message: Message, urgent: Boolean): PublishResult =
        attempt { client.put(path, Codec.encode(id, clock.millis(), message), urgent) }.fold(
            onSuccess = { PublishResult.Sent },
            onFailure = { PublishResult.Failed(it.message ?: it.javaClass.simpleName) }
        )

    private companion object {
        const val DEFAULT_REFRESH_MILLIS = 2000L
    }
}

/** Runs a Data Layer call; a failure is a [Result], but cancellation is never swallowed. */
private suspend inline fun <T> attempt(block: () -> T): Result<T> =
    runCatching { block() }.onFailure { if (it is CancellationException) throw it }

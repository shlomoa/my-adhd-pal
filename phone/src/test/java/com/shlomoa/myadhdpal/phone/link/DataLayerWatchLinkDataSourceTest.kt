package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.PublishResult
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Capabilities
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.Message
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.NodeObservation
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.PromptOption
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.Selection
import com.shlomoa.myadhdpal.protocol.Side
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataLayerWatchLinkDataSourceTest {
    private val ackId = "123e4567-e89b-42d3-a456-426614174001"
    private val selId = "123e4567-e89b-42d3-a456-426614174002"
    private val alertId = "123e4567-e89b-42d3-a456-426614174003"
    private val promptId = "123e4567-e89b-42d3-a456-426614174004"
    private val now = Instant.parse("2026-10-15T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val watchNode = "watch-node"
    private val client = FakeDataLayerClient()
    private val source = DataLayerWatchLinkDataSource(client, clock)

    private fun item(path: String, id: String, message: Message, ts: Long = 1000, node: String = watchNode) =
        RemoteItem(node, path, Codec.encode(id, ts, message))

    private val ack = Acknowledgement(AckAction.DONE, 1000)
    private val selection = Selection("calm", 1100)
    private val alert = Alert(Metric.HEART_RATE, 160.0, 150.0, 1200)
    private val watchHello = Hello(Side.WATCH, Protocol.VERSION, "0.1")

    @Test
    fun `an ack, a selection, an alert and a hello from the watch become events in order`() = runTest {
        source.ingest(
            listOf(
                item(Paths.ack(ackId), ackId, ack),
                item(Paths.selection(selId), selId, selection, 1100),
                item(Paths.alert(alertId), alertId, alert, 1200),
                item(Paths.HELLO_WATCH, "123e4567-e89b-42d3-a456-426614174009", watchHello, 1300)
            )
        )
        val events = source.events.take(4).toList()
        assertEquals(
            listOf(
                WatchEvent.AckReceived(ackId, 1000, ack),
                WatchEvent.SelectionReceived(selId, 1100, selection),
                WatchEvent.AlertReceived(alertId, 1200, alert),
                WatchEvent.HelloReceived("123e4567-e89b-42d3-a456-426614174009", 1300, watchHello)
            ),
            events
        )
        assertEquals(now, source.lastSync.value)
    }

    @Test
    fun `items written by this phone, unknown paths and undecodable bodies are dropped`() = runTest {
        source.ingest(
            listOf(
                item(Paths.ack(ackId), ackId, ack, node = client.localNodeId),
                item(Paths.prompt(promptId), promptId, prompt()),
                RemoteItem(watchNode, "/spike/del/x", Envelope(1, ackId, 1, "{}")),
                RemoteItem(watchNode, Paths.ack(selId), Envelope(1, selId, 1, "not json")),
                RemoteItem(watchNode, Paths.ack(alertId), Envelope(2, alertId, 1, "{}")),
                item(Paths.ack(ackId), ackId, ack)
            )
        )
        val events = source.events.take(1).toList()
        assertEquals(listOf(ackId), events.map { it.id })
    }

    @Test
    fun `the same item seen twice before it is stored is delivered once`() = runTest {
        val a = item(Paths.ack(ackId), ackId, ack)
        source.ingest(listOf(a))
        source.ingest(listOf(a))
        source.ingest(listOf(item(Paths.selection(selId), selId, selection, 1100)))
        assertEquals(listOf(ackId, selId), source.events.take(2).toList().map { it.id })
    }

    @Test
    fun `markStored deletes the watch-written item at its own node, a hello is not deleted`() = runTest {
        source.ingest(
            listOf(
                item(Paths.ack(ackId), ackId, ack),
                item(Paths.HELLO_WATCH, "123e4567-e89b-42d3-a456-426614174009", watchHello)
            )
        )
        val events = source.events.take(2).toList()
        events.forEach { source.markStored(it) }
        assertEquals(listOf(watchNode to Paths.ack(ackId)), client.deletes)
    }

    @Test
    fun `an item is delivered again after it was stored and written anew`() = runTest {
        source.ingest(listOf(item(Paths.ack(ackId), ackId, ack)))
        val first = source.events.take(1).toList().single()
        source.markStored(first)
        source.ingest(listOf(item(Paths.ack(ackId), ackId, ack)))
        assertEquals(ackId, source.events.take(1).toList().single().id)
    }

    @Test
    fun `publishing a schedule puts it at the schedule path with the codec envelope`() = runTest {
        val result = source.publishSchedule(schedule(1))
        assertEquals(PublishResult.Sent, result)
        val put = client.puts.single()
        assertEquals(Paths.SCHEDULE, put.path)
        assertEquals(Protocol.MAJOR_VERSION, put.envelope.version)
        assertEquals(now.toEpochMilli(), put.envelope.timestamp)
        val decoded = Codec.decode(Paths.SCHEDULE, put.envelope) as DecodeResult.Ok
        assertEquals(schedule(1), decoded.item.message)
    }

    @Test
    fun `a prompt is put urgent at its own path and removePrompt deletes the phone's own item`() = runTest {
        assertEquals(PublishResult.Sent, source.publishPrompt(promptId, prompt()))
        val put = client.puts.single()
        assertEquals(Paths.prompt(promptId), put.path)
        assertTrue(put.urgent)
        assertEquals(promptId, put.envelope.id)
        source.removePrompt(promptId)
        assertEquals(listOf(client.localNodeId to Paths.prompt(promptId)), client.deletes)
    }

    @Test
    fun `a hello is put at the phone hello path and is not urgent`() = runTest {
        val hello = Hello(Side.PHONE, Protocol.VERSION, "0.1")
        assertEquals(PublishResult.Sent, source.publishHello(hello))
        val put = client.puts.single()
        assertEquals(Paths.HELLO_PHONE, put.path)
        assertEquals(false, put.urgent)
    }

    @Test
    fun `a failing write is reported as Failed with the reason and nothing is recorded as sent`() = runTest {
        client.failPutWith = "api not available"
        val result = source.publishPrompt(promptId, prompt())
        assertTrue(result is PublishResult.Failed)
        assertTrue((result as PublishResult.Failed).reason.contains("api not available"))
        assertTrue(client.puts.isEmpty())
    }

    @Test
    fun `a schedule is urgent only when it changes the next 30 minutes`() = runTest {
        val soon = Reminder("meds", "Meds", 8 * 60 + 20, listOf(1, 2, 3, 4, 5, 6, 7))
        val later = Reminder("walk", "Walk", 12 * 60, listOf(1, 2, 3, 4, 5, 6, 7))
        // 2026-10-15 is a Thursday; the clock is 08:00 UTC
        source.publishSchedule(schedule(1, later))
        assertTrue("first publish after start is urgent", client.puts.last().urgent)
        source.publishSchedule(schedule(2, later))
        assertEquals("nothing within 30 minutes changed", false, client.puts.last().urgent)
        source.publishSchedule(schedule(3, later, soon))
        assertTrue("a reminder inside the next 30 minutes was added", client.puts.last().urgent)
        source.publishSchedule(schedule(4, soon))
        assertEquals("removing a reminder far away does not matter", false, client.puts.last().urgent)
    }

    @Test
    fun `the link state follows the node lists and the watch hello`() = runTest {
        val near = NodeObservation(watchNode, isNearby = true)
        val far = NodeObservation(watchNode, isNearby = false)
        source.refreshLinkState()
        assertEquals(LinkState.AWAY, source.linkState.value)

        client.connected = listOf(near)
        source.refreshLinkState()
        assertEquals(LinkState.NO_WATCH_APP, source.linkState.value)

        client.withCapability = mapOf(Capabilities.WATCH_APP to listOf(near))
        source.refreshLinkState()
        assertEquals(LinkState.NEARBY, source.linkState.value)

        client.connected = listOf(far)
        client.withCapability = mapOf(Capabilities.WATCH_APP to listOf(far))
        source.refreshLinkState()
        assertEquals(LinkState.RELAYED, source.linkState.value)

        source.ingest(
            listOf(
                RemoteItem(
                    watchNode,
                    Paths.HELLO_WATCH,
                    Envelope(2, "123e4567-e89b-42d3-a456-426614174009", 1, "{}")
                )
            )
        )
        source.refreshLinkState()
        assertEquals("a hello with another major version", LinkState.INCOMPATIBLE, source.linkState.value)

        source.ingest(listOf(item(Paths.HELLO_WATCH, "123e4567-e89b-42d3-a456-426614174009", watchHello, 5)))
        source.refreshLinkState()
        assertEquals(LinkState.RELAYED, source.linkState.value)
    }

    @Test
    fun `start scans the items already stored, so items sent while the phone was away arrive`() = runTest {
        client.items = listOf(item(Paths.ack(ackId), ackId, ack), item(Paths.prompt(promptId), promptId, prompt()))
        source.scanExisting()
        assertEquals(listOf(ackId), source.events.take(1).toList().map { it.id })
    }

    @Test
    fun `the link state is polled only while something collects it`() = runTest {
        val near = NodeObservation(watchNode, isNearby = true)
        client.connected = listOf(near)
        client.withCapability = mapOf(Capabilities.WATCH_APP to listOf(near))
        val polling = DataLayerWatchLinkDataSource(client, clock, refreshMillis = 2000)
        polling.start(backgroundScope)
        runCurrent()
        assertEquals("one refresh at start", LinkState.NEARBY, polling.linkState.value)

        val collector = launch { polling.linkState.collect { } }
        runCurrent()
        client.connected = emptyList()
        client.withCapability = emptyMap()
        advanceTimeBy(2100)
        assertEquals("refreshed while collected", LinkState.AWAY, polling.linkState.value)

        collector.cancel()
        runCurrent()
        client.connected = listOf(near)
        client.withCapability = mapOf(Capabilities.WATCH_APP to listOf(near))
        advanceTimeBy(10_000)
        assertEquals("no polling when nobody collects", LinkState.AWAY, polling.linkState.value)
    }

    private fun prompt() = Prompt("Take a break?", listOf(PromptOption("1", "Yes"), PromptOption("2", "Later")), null)

    private fun schedule(version: Long, vararg reminders: Reminder) =
        Schedule(version, reminders.toList(), emptyList(), SnoozeSettings(10, 3), AlertSettings(emptyList(), 600))
}

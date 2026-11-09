package com.shlomoa.myadhdpal.wear.monitor

import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.AlertRules
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.EpisodeEnd
import com.shlomoa.myadhdpal.protocol.ItemPath
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.alerts.Reading
import com.shlomoa.myadhdpal.protocol.fake.FakeLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The path from a reading to the local response and the urgent alert; the service and the debug injector both call
 * [MonitorHub.feed].
 */
class MonitorHubTest {
    private val link = FakeLink().apply { connect(nearby = true) }
    private val events = mutableListOf<String>()
    private val logged = mutableListOf<String>()
    private var clock = 5_000_000L
    private val ids = ArrayDeque((1..9).map { "00000000-0000-4000-8000-00000000000$it" })
    private val responder = object : AlertResponder {
        override fun respond(alert: Alert) {
            events += "respond level=${alert.level}"
        }
    }
    private val sender = TransportAlertSender({ link.watch }, { ids.removeFirst() }, { clock })
    private val sendingRecorder = object : AlertSender {
        override fun send(alert: Alert) {
            events += "send level=${alert.level}"
            sender.send(alert)
        }
    }
    private val hub = MonitorHub(
        monitor = StressMonitor(
            AlertSettings(AlertRules.DEFAULT_COOLDOWN_SECONDS),
            { "00000000-0000-4000-8000-0000000000f${ids.size}" }
        ),
        responder = responder,
        sender = sendingRecorder,
        now = { clock },
        log = { logged += it }
    )

    private fun hr(value: Double, at: Long) = Reading(Metric.HEART_RATE, value, at)

    @Test
    fun `TC-W-077 an alert is sent as an urgent alert item with a unique id and the numeric value`() {
        hub.feed(listOf(hr(120.0, clock)))
        val items = link.watch.items().filter { it.path.startsWith(Paths.ALERT_PREFIX) }
        assertEquals(1, items.size)
        val decoded = Codec.decodeIfCompatible(items.single().path, items.single().envelope) as DecodeResult.Ok
        val alert = decoded.item.message as Alert
        assertEquals(120.0, alert.value, 0.0)
        assertEquals(clock, alert.at)
        assertTrue(Paths.parse(items.single().path) is ItemPath.Alert)
    }

    @Test
    fun `TC-W-236 the local response comes before the send, in the same call as the reading`() {
        hub.feed(listOf(hr(120.0, clock)))
        assertEquals(listOf("respond level=1", "send level=1"), events)
        assertTrue(hub.state.stress)
        assertEquals(1, hub.state.level)
    }

    @Test
    fun `TC-W-237 a link that is down does not stop the local response and the alert is queued for the phone`() {
        link.disconnect()
        hub.feed(listOf(hr(120.0, clock)))
        assertEquals("respond level=1", events.first())
        assertTrue(link.phone.items().none { it.path.startsWith(Paths.ALERT_PREFIX) })
        link.connect(nearby = true)
        assertEquals(1, link.phone.items().count { it.path.startsWith(Paths.ALERT_PREFIX) })
    }

    @Test
    fun `TC-W-084 the end of an episode from the phone stops the state, the next breach is a new level 1 episode`() {
        hub.feed(listOf(hr(120.0, clock)))
        val id = hub.state.episodeId!!
        assertTrue(hub.endEpisode(id))
        assertFalse(hub.state.stress)
        hub.feed(listOf(hr(125.0, clock + 1_000)))
        assertEquals(2, events.count { it == "respond level=1" })
        assertFalse(hub.endEpisode(id))
    }

    @Test
    fun `TC-W-085 I'm OK on the watch ends the episode and stops the repeats`() {
        hub.feed(listOf(hr(120.0, clock)))
        hub.imOk()
        assertFalse(hub.state.stress)
        assertEquals(null, hub.state.episodeId)
        hub.feed(listOf(hr(125.0, clock + 1_000)))
        assertEquals(2, events.count { it == "respond level=1" })
    }

    @Test
    fun `TC-W-238 the state shows the latest reading, the baseline and the threshold after every call`() {
        val seen = mutableListOf<MonitorState>()
        hub.addListener { seen += it }
        hub.feed(listOf(hr(80.0, clock)))
        hub.feed(listOf(hr(81.0, clock + 1_000)))
        assertEquals(2, seen.size)
        assertEquals(81.0, seen.last().latestBpm!!, 0.0)
        assertEquals(AlertRules.DEFAULT_INITIAL_BPM.toDouble(), seen.last().thresholdBpm, 0.0)
        assertEquals(2, seen.last().readings)
    }

    @Test
    fun `TC-W-081 nothing the hub logs holds a heart-rate value`() {
        hub.feed(listOf(hr(123.0, clock), hr(77.0, clock + 1_000)))
        assertTrue(logged.isNotEmpty())
        assertTrue(logged.none { Regex("""\b(123|77)\b""").containsMatchIn(it) })
    }

    @Test
    fun `TC-W-239 an end-of-episode item from the phone is applied through the item path`() {
        hub.feed(listOf(hr(120.0, clock)))
        val id = hub.state.episodeId!!
        link.phone.put(Paths.episodeEnd(id), Codec.encode(id, clock, EpisodeEnd(id, clock)))
        val stored = link.watch.items().single { it.path == Paths.episodeEnd(id) }
        hub.onItem(stored)
        assertFalse(hub.state.stress)
    }
}

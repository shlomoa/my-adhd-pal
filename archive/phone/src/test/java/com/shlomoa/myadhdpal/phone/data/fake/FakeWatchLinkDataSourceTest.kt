package com.shlomoa.myadhdpal.phone.data.fake

import com.shlomoa.myadhdpal.phone.data.PublishResult
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.HeartRatePermission
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.Side
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import com.shlomoa.myadhdpal.protocol.WatchStatus
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeWatchLinkDataSourceTest {
    private val id1 = "123e4567-e89b-42d3-a456-426614174001"
    private val id2 = "123e4567-e89b-42d3-a456-426614174002"
    private val schedule = Schedule(1, emptyList(), emptyList(), SnoozeSettings(10, 3), AlertSettings(600))

    private fun ack(id: String, at: Long) = WatchEvent.AckReceived(id, at, Acknowledgement(AckAction.DONE, at))

    @Test
    fun `TC-S-165 the fake holds the last watch status the test sets`() = runTest {
        val source = FakeWatchLinkDataSource()
        assertEquals(null, source.watchStatus.value)
        val status = WatchStatus(thresholdBpm = 90, heartRatePermission = HeartRatePermission.GRANTED)
        source.setWatchStatus(status)
        assertEquals(status, source.watchStatus.value)
    }

    @Test
    fun `TC-S-162 the fake records the episode ends the app publishes and can fail them`() = runTest {
        val source = FakeWatchLinkDataSource()
        assertEquals(PublishResult.Sent, source.publishEpisodeEnd(id1))
        source.failNextPublish("offline")
        assertTrue(source.publishEpisodeEnd(id2) is PublishResult.Failed)
        assertEquals(listOf(id1), source.publishedEpisodeEnds)
    }

    @Test
    fun `TC-S-090 the fake emits what the test publishes, in order`() = runTest {
        val source = FakeWatchLinkDataSource()
        source.connect(nearby = true)
        source.emit(ack(id1, 1))
        source.emit(ack(id2, 2))
        val received = source.events.take(2).toList()
        assertEquals(listOf(id1, id2), received.map { it.id })
    }

    @Test
    fun `TC-S-090 the fake records what the app publishes`() = runTest {
        val source = FakeWatchLinkDataSource()
        assertEquals(PublishResult.Sent, source.publishSchedule(schedule))
        assertEquals(PublishResult.Sent, source.publishHello(Hello(Side.PHONE, Protocol.VERSION, "1.0")))
        assertEquals(listOf(schedule), source.publishedSchedules)
        assertEquals(1, source.publishedHellos.size)
    }

    @Test
    fun `TC-S-090 a failed publish is reported and not recorded as sent`() = runTest {
        val source = FakeWatchLinkDataSource()
        source.failNextPublish("link error")
        assertEquals(PublishResult.Failed("link error"), source.publishSchedule(schedule))
        assertTrue(source.publishedSchedules.isEmpty())
        assertEquals(PublishResult.Sent, source.publishSchedule(schedule))
        assertEquals(1, source.publishedSchedules.size)
    }

    @Test
    fun `TC-S-090 markStored is recorded so a test can check delete-after-store`() = runTest {
        val source = FakeWatchLinkDataSource()
        val event = ack(id1, 1)
        source.markStored(event)
        assertEquals(listOf(id1), source.storedIds)
    }

    @Test
    fun `TC-S-091 disconnect and reconnect surface as link state changes`() = runTest {
        val source = FakeWatchLinkDataSource()
        assertEquals(LinkState.AWAY, source.linkState.value)
        source.connect(nearby = true)
        assertEquals(LinkState.NEARBY, source.linkState.value)
        source.connect(nearby = false)
        assertEquals(LinkState.RELAYED, source.linkState.value)
        source.disconnect()
        assertEquals(LinkState.AWAY, source.linkState.value)
        source.setLinkState(LinkState.INCOMPATIBLE)
        assertEquals(LinkState.INCOMPATIBLE, source.linkState.value)
    }

    @Test
    fun `TC-S-091 events emitted while disconnected arrive on reconnect and update last sync`() = runTest {
        val source = FakeWatchLinkDataSource(now = { Instant.parse("2026-10-14T12:00:00Z") })
        assertEquals(null, source.lastSync.value)
        source.emit(ack(id1, 1))
        // away: held back
        assertEquals(LinkState.AWAY, source.linkState.value)
        assertEquals(null, source.lastSync.value)
        source.connect(nearby = true)
        assertEquals(id1, source.events.first().id)
        assertEquals(Instant.parse("2026-10-14T12:00:00Z"), source.lastSync.value)
    }
}

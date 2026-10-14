package com.shlomoa.myadhdpal.wear.schedule

import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.ScheduleLogic
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import com.shlomoa.myadhdpal.protocol.fake.FakeLink
import com.shlomoa.myadhdpal.wear.link.FanOutListener
import com.shlomoa.myadhdpal.wear.link.LinkClient
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleReceiverTest {
    private val link = FakeLink()
    private val store = InMemoryScheduleStore()
    private var now = Instant.parse("2026-10-14T08:00:00Z")
    private val applied = mutableListOf<Long>()
    private val scheduleId = "33333333-3333-4333-8333-333333333333"

    private fun schedule(version: Long, vararg reminders: Reminder) = Schedule(
        version,
        reminders.toList(),
        emptyList(),
        SnoozeSettings(minutes = 10, maxCount = 3),
        AlertSettings(emptyList(), cooldownSeconds = 0)
    )

    private val standUp = Reminder("r1", "Stand up", minuteOfDay = 9 * 60, days = listOf(1, 2, 3, 4, 5, 6, 7))

    private fun receiver() = ScheduleReceiver(
        transport = link.watch,
        store = store,
        now = { now },
        onApplied = { schedule, _ -> applied += schedule.version }
    ).also { link.watch.setListener(it) }

    private fun send(schedule: Schedule) =
        link.phone.put(Paths.SCHEDULE, Codec.encode(scheduleId, now.toEpochMilli(), schedule))

    @Test
    fun `TC-W-010 a schedule with a newer version replaces the stored one`() {
        val receiver = receiver().apply { start() }
        link.connect(nearby = true)
        send(schedule(1, standUp))
        send(schedule(2, standUp, standUp.copy(id = "r2", title = "Drink water")))
        assertEquals(2L, receiver.current?.version)
        assertEquals(2L, store.load()?.schedule?.version)
        assertEquals(listOf(1L, 2L), applied)
    }

    @Test
    fun `TC-W-011 a schedule with an equal or older version is ignored`() {
        val receiver = receiver().apply { start() }
        link.connect(nearby = true)
        send(schedule(5, standUp))
        send(schedule(5, standUp.copy(title = "Same version, different text")))
        send(schedule(4, standUp.copy(title = "Older")))
        assertEquals("Stand up", receiver.current?.reminders?.single()?.title)
        assertEquals(listOf(5L), applied)
    }

    @Test
    fun `TC-W-012 an invalid or unsupported-major schedule item is dropped`() {
        val receiver = receiver().apply { start() }
        link.connect(nearby = true)
        val good = Codec.encode(scheduleId, now.toEpochMilli(), schedule(1, standUp))
        link.phone.put(Paths.SCHEDULE, good.copy(body = "{ not json"))
        assertNull(receiver.current)
        link.phone.put(Paths.SCHEDULE, good.copy(version = Protocol.MAJOR_VERSION + 1))
        assertNull(receiver.current)
        assertNull(store.load())
        assertTrue(applied.isEmpty())
    }

    @Test
    fun `TC-W-014 after the first sync reminders resolve from storage with no phone present`() {
        receiver().apply { start() }
        link.connect(nearby = true)
        send(schedule(1, standUp))
        link.disconnect()

        val restarted = ScheduleReceiver(link.watch, store, { now }, { _, _ -> })
        restarted.start()
        val next = ScheduleLogic.nextReminder(restarted.current!!, now, ZoneId.of("UTC"))
        assertEquals(Instant.parse("2026-10-14T09:00:00Z"), next?.at)
    }

    @Test
    fun `TC-W-016 the watch never deletes the phone's schedule item`() {
        val receiver = receiver().apply { start() }
        link.connect(nearby = true)
        send(schedule(1, standUp))
        assertNotNull(receiver.current)
        assertTrue(link.watch.items().any { it.path == Paths.SCHEDULE })
        assertTrue(link.phone.items().any { it.path == Paths.SCHEDULE })
    }

    @Test
    fun `TC-W-010 a schedule already stored on the watch when the service starts is applied`() {
        link.connect(nearby = true)
        send(schedule(3, standUp))
        val receiver = receiver().apply { start() }
        assertEquals(3L, receiver.current?.version)
    }

    @Test
    fun `TC-W-005 an applied schedule reports its apply time to the link client`() {
        val client = LinkClient(link.watch, "0.1.0", { "44444444-4444-4444-8444-444444444444" }, { now }, null)
        val receiver = ScheduleReceiver(link.watch, store, { now }, { _, at -> client.onScheduleApplied(at) })
        link.watch.setListener(FanOutListener(client, receiver))
        receiver.start()
        link.connect(nearby = true)
        send(schedule(1, standUp))
        assertEquals(now, client.view.lastSyncAt)
        assertEquals(com.shlomoa.myadhdpal.protocol.LinkState.NEARBY, client.view.state)
    }

    @Test
    fun `TC-W-012 an envelope without any body is dropped`() {
        val receiver = receiver().apply { start() }
        link.connect(nearby = true)
        link.phone.put(Paths.SCHEDULE, Envelope(Protocol.MAJOR_VERSION, scheduleId, now.toEpochMilli(), ""))
        assertNull(receiver.current)
    }
}

package com.shlomoa.myadhdpal.wear.demo

import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.wear.log.LogLevel
import com.shlomoa.myadhdpal.wear.log.Logger
import com.shlomoa.myadhdpal.wear.scheduler.FiredReminder
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The demo script (scripts/demo/run-demo.ps1) reads these lines from `adb logcat -s DEMO`; this pins their format. */
class DemoLogTest {
    private val lines = mutableListOf<Pair<String, String>>()

    init {
        Logger.sink = com.shlomoa.myadhdpal.wear.log.LogSink { level, tag, message ->
            if (level == LogLevel.Info) lines += tag to message
        }
    }

    @After
    fun reset() {
        Logger.sink = null
    }

    @Test
    fun `TC-W-100 a schedule applied is logged with its version`() {
        DemoLog.scheduleApplied(version = 7)
        assertEquals(listOf("DEMO" to "schedule applied version=7"), lines)
    }

    @Test
    fun `TC-W-100 a fired reminder is logged with its id and occurrence but not its title`() {
        val fired =
            FiredReminder(Reminder("demo", "Private title", 0, listOf(1)), 1, Instant.ofEpochMilli(1_760_000_120_000))
        DemoLog.reminderFired(fired)
        assertEquals(listOf("DEMO" to "reminder fired id=demo occurrenceAt=1760000120000 snoozeCount=1"), lines)
    }

    @Test
    fun `TC-W-100 an acknowledgement sent is logged with action, reminder and occurrence`() {
        DemoLog.ackSent(AckAction.DONE, "demo", Instant.ofEpochMilli(1_760_000_120_000))
        assertEquals(listOf("DEMO" to "ack sent action=DONE reminderId=demo occurrenceAt=1760000120000"), lines)
    }

    @Test
    fun `TC-W-100 a missing transport is logged so the script can say why no ack arrived`() {
        DemoLog.ackNotSent(AckAction.DONE, "demo", "no transport")
        assertEquals(listOf("DEMO" to "ack not sent action=DONE reminderId=demo reason=no transport"), lines)
    }
}

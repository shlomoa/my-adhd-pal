package com.shlomoa.myadhdpal.phone.debug

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.shlomoa.myadhdpal.phone.alert.AlertPlan
import com.shlomoa.myadhdpal.phone.alert.AlertPlanner
import com.shlomoa.myadhdpal.phone.alert.AlertPrefs
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.protocol.Metric
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The `DEMO alert` lines the stress-demo script reads to PASS or FAIL the phone stage (ids and times only). */
@RunWith(RobolectricTestRunner::class)
class DemoAlertTraceTest {
    private val lines = mutableListOf<String>()
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val nowMs = now.toEpochMilli()
    private val trace = DemoAlertTrace(Clock.fixed(now, ZoneOffset.UTC), lines::add)

    private fun alert(id: String, at: Long) = HistoryEntry.AlertRaised(id, at, Metric.HEART_RATE, 150.0, 120.0)

    private fun plan(sound: Boolean) = AlertPlanner.plan(1, AlertPrefs(soundOn = sound), hasAmplitudeControl = true)

    @Test
    fun `TC-A-142 a new alert logs its stored time and its raised time with the sound flag, live`() {
        val a = alert("a-1", 1_000)
        trace.stored(a)
        trace.raised(a, plan(sound = true))
        assertEquals(
            listOf(
                "DEMO alert stored id=a-1 watchAt=1000 storedAt=$nowMs",
                "DEMO alert raised id=a-1 raisedAt=$nowMs sound=1"
            ),
            lines
        )
    }

    @Test
    fun `TC-A-143 the dump lists each alert row with the times seen so far and none for the rest, then the end`() {
        val seen = alert("a-1", 1_000)
        trace.stored(seen)
        trace.raised(seen, plan(sound = false))
        lines.clear()
        trace.dump(listOf(seen, alert("a-0", 500)))
        assertEquals(
            listOf(
                "DEMO alert stored id=a-1 watchAt=1000 storedAt=$nowMs",
                "DEMO alert raised id=a-1 raisedAt=$nowMs sound=0",
                "DEMO alert stored id=a-0 watchAt=500 storedAt=none",
                "DEMO alert raised id=a-0 raisedAt=none sound=none",
                "DEMO alerts end count=2"
            ),
            lines
        )
    }

    @Test
    fun `TC-A-144 an empty dump logs only the end line and the lines hold no health value`() {
        trace.dump(emptyList())
        assertEquals(listOf("DEMO alerts end count=0"), lines)
        val a = alert("a-9", 1)
        trace.stored(a)
        trace.raised(a, plan(sound = true))
        assertTrue(lines.drop(1).none { "150" in it || "120" in it || "HEART" in it })
    }

    @Test
    fun `TC-A-145 the alerts receiver is exported and protected by the DUMP permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(DemoActions.DUMP_ALERTS).setPackage(context.packageName),
            PackageManager.GET_RESOLVED_FILTER
        )
        val info = receivers.singleOrNull()?.activityInfo
        assertNotNull("no receiver for ${DemoActions.DUMP_ALERTS}", info)
        assertTrue(info!!.exported)
        assertEquals("android.permission.DUMP", info.permission)
    }
}

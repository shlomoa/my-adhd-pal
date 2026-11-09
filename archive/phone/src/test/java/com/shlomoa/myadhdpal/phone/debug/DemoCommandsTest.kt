package com.shlomoa.myadhdpal.phone.debug

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryHistoryRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.fake.InMemorySettingsRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Selection
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the two debug broadcasts log. The Watch lane's demo script (scripts/demo) greps these exact lines. */
@RunWith(RobolectricTestRunner::class)
class DemoCommandsTest {
    private val tasks = InMemoryTaskRepository()
    private val history = InMemoryHistoryRepository()
    private val source = FakeWatchLinkDataSource()
    private val schedule = InMemoryScheduleRepository(
        tasks,
        InMemorySettingsRepository(),
        source,
        InMemoryScheduleVersionStore()
    )
    private val lines = mutableListOf<String>()
    private val noon = Instant.parse("2026-10-14T12:00:00Z")
    private val reminderAt = noon.plusSeconds(120).toEpochMilli()

    private val commands = DemoCommands(
        DemoSchedulePublisher(tasks, schedule, Clock.fixed(noon, ZoneOffset.UTC)),
        history,
        lines::add
    )

    @Test
    fun `TC-A-081 publishing logs the version and the reminder time, or unchanged`() = runTest {
        commands.publishSchedule(minutesAhead = null, title = null)
        commands.publishSchedule(minutesAhead = null, title = null)
        assertEquals(
            listOf(
                "DEMO published schedule version=1 reminderAt=$reminderAt",
                "DEMO published schedule unchanged reminderAt=$reminderAt"
            ),
            lines
        )
    }

    @Test
    fun `TC-A-082 a failed or invalid publish is logged and not thrown`() = runTest {
        source.failNextPublish("link down")
        commands.publishSchedule(minutesAhead = null, title = null)
        commands.publishSchedule(minutesAhead = 0, title = null)
        commands.publishSchedule(minutesAhead = 1441, title = "x")
        assertEquals("DEMO publish failed reason=link down", lines[0])
        assertTrue(lines[1].startsWith("DEMO publish failed reason="))
        assertTrue(lines[2].startsWith("DEMO publish failed reason="))
        assertEquals(3, lines.size)
    }

    @Test
    fun `TC-A-083 the history dump logs ack, selection and alert rows newest first, then the end line`() = runTest {
        history.store(WatchEvent.AckReceived("a-1", 1, Acknowledgement(AckAction.DONE, 1_000, "demo", 900)))
        history.store(WatchEvent.AckReceived("a-2", 2, Acknowledgement(AckAction.SNOOZE, 500)))
        history.store(WatchEvent.SelectionReceived("s-1", 3, Selection("2", 2_000)))
        history.store(WatchEvent.AlertReceived("l-1", 4, Alert(Metric.HEART_RATE, 171.0, 150.0, 3_000)))
        commands.dumpHistory()
        assertEquals(
            listOf(
                "DEMO history alert id=l-1 metric=HEART_RATE value=171.0 threshold=150.0 at=3000",
                "DEMO history selection id=s-1 key=2 at=2000",
                "DEMO history ack id=a-1 action=DONE reminderId=demo occurrenceAt=900 at=1000",
                "DEMO history ack id=a-2 action=SNOOZE reminderId=none occurrenceAt=none at=500",
                "DEMO history end count=4"
            ),
            lines
        )
    }

    @Test
    fun `TC-A-084 an empty history logs only the end line`() = runTest {
        commands.dumpHistory()
        assertEquals(listOf("DEMO history end count=0"), lines)
    }

    @Test
    fun `TC-A-085 both receivers are exported and protected by the DUMP permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (action in listOf(DemoActions.PUBLISH_DEMO_SCHEDULE, DemoActions.DUMP_HISTORY)) {
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                PackageManager.GET_RESOLVED_FILTER
            )
            val info = receivers.singleOrNull()?.activityInfo
            assertNotNull("no receiver for $action", info)
            assertTrue("$action receiver is not exported", info!!.exported)
            assertEquals("android.permission.DUMP", info.permission)
        }
    }
}

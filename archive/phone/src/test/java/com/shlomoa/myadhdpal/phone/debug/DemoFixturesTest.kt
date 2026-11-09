package com.shlomoa.myadhdpal.phone.debug

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryHistoryRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The example data the phone walkthrough script puts on the phone through a debug broadcast. */
@RunWith(RobolectricTestRunner::class)
class DemoFixturesTest {
    private val tasks = InMemoryTaskRepository()
    private val history = InMemoryHistoryRepository()
    private val lines = mutableListOf<String>()
    private var millis = 1_760_000_000_000L
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant(): Instant = Instant.ofEpochMilli(millis++)
    }
    private val fixtures = DemoFixtures(tasks, history, clock, lines::add)

    @Test
    fun `TC-A-150 seeding puts two tasks with reminders and three history rows, and again changes nothing`() = runTest {
        fixtures.seed(withAlert = false)
        fixtures.seed(withAlert = false)
        assertEquals(2, tasks.tasks.first().size)
        assertEquals(2, tasks.reminders.first().size)
        assertEquals(3, history.history(null).first().size)
        assertEquals(List(2) { "DEMO seeded history=3 tasks=2 alert=none" }, lines)
    }

    @Test
    fun `TC-A-151 seeding with an alert adds one new alert row each time and names its id`() = runTest {
        fixtures.seed(withAlert = true)
        fixtures.seed(withAlert = true)
        val alerts = history.history(null).first().filterIsInstance<HistoryEntry.AlertRaised>()
        assertEquals(2, alerts.size)
        assertNotEquals(alerts[0].id, alerts[1].id)
        assertTrue(alerts.all { it.episodeId != null })
        assertEquals("DEMO seeded history=4 tasks=2 alert=${alerts.last().id}", lines.first())
        assertTrue(lines.all { "138" !in it && "HEART" !in it })
    }

    @Test
    fun `TC-A-152 the seed and dismiss receivers are exported and protected by the DUMP permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (action in listOf(DemoActions.SEED_FIXTURES, DemoActions.DISMISS_ALERT)) {
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

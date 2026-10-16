package com.shlomoa.myadhdpal.phone.alert

import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryHistoryRepository
import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.Metric
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingNotifier : AlertNotifier {
    val raised = mutableListOf<Pair<HistoryEntry.AlertRaised, AlertPlan>>()
    var stops = 0

    override fun raise(alert: HistoryEntry.AlertRaised, plan: AlertPlan) {
        raised += alert to plan
    }

    override fun stop() {
        stops++
    }
}

private class MemoryRaisedStore(var ids: Set<String>? = null) : RaisedAlertsStore {
    override suspend fun load(): Set<String>? = ids

    override suspend fun save(ids: Set<String>) {
        this.ids = ids
    }
}

/** What `AlertRaiser` raises, from the stored alert rows. The watch time is `at`; the arrival order is the order stored. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertRaiserTest {
    private val history = InMemoryHistoryRepository()
    private val notifier = RecordingNotifier()
    private val store = MemoryRaisedStore()

    private fun alert(id: String, at: Long) =
        WatchEvent.AlertReceived(id, at, Alert(Metric.HEART_RATE, 138.0, 110.0, at))

    private fun raiser(store: RaisedAlertsStore = this.store, prefs: AlertPrefs = AlertPrefs()) =
        AlertRaiser(history, store, notifier, prefsProvider = { prefs }, hasAmplitudeControl = { true })

    private fun ids() = notifier.raised.map { it.first.id }

    @Test
    fun `TC-A-094 a stored alert is raised once and not again`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { raiser().run() }
        history.store(alert("a-1", 1_000))
        history.store(alert("a-1", 1_000))
        assertEquals(listOf("a-1"), ids())
        history.store(alert("a-2", 2_000))
        assertEquals(listOf("a-1", "a-2"), ids())
        job.cancel()
    }

    @Test
    fun `TC-A-095 several alerts arriving together raise only the newest and mark all as raised`() = runTest {
        history.store(alert("old", 100))
        store.ids = setOf("old")
        history.store(alert("a-1", 1_000))
        history.store(alert("a-2", 3_000))
        history.store(alert("a-3", 2_000))
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { raiser().run() }
        assertEquals(listOf("a-2"), ids())
        assertTrue(store.ids!!.containsAll(listOf("old", "a-1", "a-2", "a-3")))
        job.cancel()
    }

    @Test
    fun `TC-A-096 an alert with an older watch time arriving later is still raised`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { raiser().run() }
        history.store(alert("a-new", 9_000))
        history.store(alert("a-late", 1_000))
        assertEquals(listOf("a-new", "a-late"), ids())
        job.cancel()
    }

    @Test
    fun `TC-A-097 alerts already in the history when the feature first runs are not raised, the next one is`() = runTest {
        history.store(alert("before-1", 1_000))
        history.store(alert("before-2", 2_000))
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { raiser().run() }
        assertEquals(emptyList<String>(), ids())
        history.store(alert("after", 3_000))
        assertEquals(listOf("after"), ids())
        job.cancel()
    }

    @Test
    fun `TC-A-098 after a restart nothing raised before is raised again, and an alert stored but not raised is`() = runTest {
        val first = launch(UnconfinedTestDispatcher(testScheduler)) { raiser().run() }
        history.store(alert("a-1", 1_000))
        first.cancel()
        assertEquals(listOf("a-1"), ids())

        // The process died after the row was stored and before the alert was raised.
        history.store(alert("a-2", 2_000))

        val second = launch(UnconfinedTestDispatcher(testScheduler)) { raiser().run() }
        assertEquals(listOf("a-1", "a-2"), ids())
        second.cancel()
        assertEquals(1, history.history(HistoryType.ALERT).first().count { it.id == "a-1" })
    }

    @Test
    fun `TC-A-099 I'm OK stops the notifier`() = runTest {
        raiser().dismiss()
        assertEquals(1, notifier.stops)
    }

    @Test
    fun `only the ids of alerts still in the history are kept`() = runTest {
        history.store(alert("a-1", 1_000))
        store.ids = setOf("a-1", "forgotten-because-no-longer-stored")
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { raiser().run() }
        history.store(alert("a-2", 2_000))
        assertEquals(setOf("a-1", "a-2"), store.ids)
        job.cancel()
    }

    @Test
    fun `the plan handed to the notifier follows the preferences`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            raiser(prefs = AlertPrefs(soundOn = false, flashOn = false)).run()
        }
        history.store(alert("a-1", 1_000))
        val plan = notifier.raised.single().second
        assertEquals(false, plan.sound)
        assertEquals(null, plan.flash)
        job.cancel()
    }
}

package com.shlomoa.myadhdpal.phone.ui.history

import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryHistoryRepository
import com.shlomoa.myadhdpal.phone.ui.ListState
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Selection
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Step 4.5: the history of acknowledgements, selections and alerts. */
class HistoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = InMemoryHistoryRepository()
    // Lazy: created after [main] has installed the test dispatcher.
    private val vm by lazy { HistoryViewModel(repo) }

    private fun ack(id: String, at: Long, action: AckAction = AckAction.DONE) =
        WatchEvent.AckReceived(id, at, Acknowledgement(action, at, reminderId = "r-1", occurrenceAt = at))

    private fun selection(id: String, at: Long) = WatchEvent.SelectionReceived(id, at, Selection("2", at))

    private fun alert(id: String, at: Long) =
        WatchEvent.AlertReceived(id, at, Alert(Metric.HEART_RATE, 171.0, 150.0, at))

    private fun entries() = (vm.uiState.value.entries as ListState.Content).items

    @Test
    fun `TC-A-045 acknowledgements are listed with action and time`() = runTest {
        repo.store(ack("a-1", 1_000))
        val entry = entries().single() as HistoryEntry.Acknowledged
        assertEquals(AckAction.DONE, entry.action)
        assertEquals(1_000L, entry.at)
    }

    @Test
    fun `TC-A-046 selections are listed with the chosen key and time`() = runTest {
        repo.store(selection("s-1", 2_000))
        val entry = entries().single() as HistoryEntry.Selected
        assertEquals("2", entry.key)
        assertEquals(2_000L, entry.at)
    }

    @Test
    fun `TC-A-047 alerts are listed with metric, value and threshold`() = runTest {
        repo.store(alert("l-1", 3_000))
        val entry = entries().single() as HistoryEntry.AlertRaised
        assertEquals(Triple(Metric.HEART_RATE, 171.0, 150.0), Triple(entry.metric, entry.value, entry.threshold))
    }

    @Test
    fun `TC-A-048 entries are newest first`() = runTest {
        repo.store(ack("a-1", 1_000))
        repo.store(alert("l-1", 3_000))
        repo.store(selection("s-1", 2_000))
        assertEquals(listOf("l-1", "s-1", "a-1"), entries().map { it.id })
    }

    @Test
    fun `TC-A-049 filter by type`() = runTest {
        repo.store(ack("a-1", 1_000))
        repo.store(alert("l-1", 3_000))
        vm.setFilter(HistoryType.ALERT)
        assertEquals(listOf("l-1"), entries().map { it.id })
        assertEquals(HistoryType.ALERT, vm.uiState.value.filter)
        vm.setFilter(null)
        assertEquals(2, entries().size)
    }

    @Test
    fun `TC-A-050 a new item appears without a reload`() = runTest {
        repo.store(ack("a-1", 1_000))
        assertEquals(1, entries().size)
        repo.store(alert("l-1", 3_000))
        assertEquals(2, entries().size)
    }

    @Test
    fun `TC-A-051 a snooze is recorded as an acknowledgement`() = runTest {
        repo.store(ack("a-1", 1_000, AckAction.SNOOZE))
        assertEquals(AckAction.SNOOZE, (entries().single() as HistoryEntry.Acknowledged).action)
    }

    @Test
    fun `TC-A-054 empty history is the Empty state`() {
        assertEquals(ListState.Empty, vm.uiState.value.entries)
    }

    @Test
    fun `TC-A-056 a failing history source is Failed and retry reloads`() {
        var broken = true
        val flaky = object : HistoryRepository by repo {
            override fun history(type: HistoryType?): Flow<List<HistoryEntry>> = flow {
                if (broken) throw IOException("disk")
                emit(emptyList())
            }
        }
        val failing = HistoryViewModel(flaky)
        assertTrue(failing.uiState.value.entries is ListState.Failed)
        broken = false
        failing.retry()
        assertEquals(ListState.Empty, failing.uiState.value.entries)
    }
}

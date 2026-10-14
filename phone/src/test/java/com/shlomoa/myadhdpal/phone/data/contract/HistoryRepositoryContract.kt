package com.shlomoa.myadhdpal.phone.data.contract

import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.data.StoreResult
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Selection
import com.shlomoa.myadhdpal.protocol.Side
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every [HistoryRepository] must pass these. */
abstract class HistoryRepositoryContract {
    abstract fun create(): HistoryRepository

    private val a1 = "123e4567-e89b-42d3-a456-426614174001"
    private val s1 = "123e4567-e89b-42d3-a456-426614174002"
    private val l1 = "123e4567-e89b-42d3-a456-426614174003"

    private fun ack(id: String, at: Long) = WatchEvent.AckReceived(id, at, Acknowledgement(AckAction.SNOOZE, at))

    @Test
    fun `TC-S-092 history repository stores the three event types and lists them newest first`() = runTest {
        val repo = create()
        assertEquals(StoreResult.Stored, repo.store(ack(a1, 100)))
        assertEquals(StoreResult.Stored, repo.store(WatchEvent.SelectionReceived(s1, 300, Selection("calm", 300))))
        assertEquals(StoreResult.Stored, repo.store(WatchEvent.AlertReceived(l1, 200, Alert(Metric.HEART_RATE, 163.5, 150.0, 200))))
        val entries = repo.history().first()
        assertEquals(listOf(s1, l1, a1), entries.map { it.id })
        assertEquals(HistoryEntry.Acknowledged(a1, 100, AckAction.SNOOZE), entries[2])
        assertEquals(HistoryEntry.AlertRaised(l1, 200, Metric.HEART_RATE, 163.5, 150.0), entries[1])
        assertEquals("calm", (entries[0] as HistoryEntry.Selected).key)
    }

    @Test
    fun `TC-S-092 history repository filters by type`() = runTest {
        val repo = create()
        repo.store(ack(a1, 100))
        repo.store(WatchEvent.SelectionReceived(s1, 300, Selection("calm", 300)))
        assertEquals(listOf(a1), repo.history(HistoryType.ACKNOWLEDGEMENT).first().map { it.id })
        assertEquals(listOf(s1), repo.history(HistoryType.SELECTION).first().map { it.id })
        assertEquals(emptyList<HistoryEntry>(), repo.history(HistoryType.ALERT).first())
    }

    @Test
    fun `TC-S-092 history repository is idempotent by id`() = runTest {
        val repo = create()
        assertEquals(StoreResult.Stored, repo.store(ack(a1, 100)))
        assertEquals(StoreResult.Duplicate, repo.store(ack(a1, 100)))
        assertEquals(1, repo.history().first().size)
    }

    @Test
    fun `TC-S-092 history repository ignores events that are not history`() = runTest {
        val repo = create()
        val hello = WatchEvent.HelloReceived(a1, 1, Hello(Side.WATCH, Protocol.VERSION, "1.0"))
        assertEquals(StoreResult.Ignored, repo.store(hello))
        assertEquals(emptyList<HistoryEntry>(), repo.history().first())
    }
}

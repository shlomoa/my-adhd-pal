package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.data.StoreResult
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryHistoryRepository
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Side
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchEventProcessorTest {
    private val id1 = "123e4567-e89b-42d3-a456-426614174001"
    private val id2 = "123e4567-e89b-42d3-a456-426614174002"
    private fun ack(id: String, at: Long) = WatchEvent.AckReceived(id, at, Acknowledgement(AckAction.DONE, at))

    @Test
    fun `an event from the watch becomes a history row and only then is marked stored`() = runTest {
        val source = FakeWatchLinkDataSource()
        val history = InMemoryHistoryRepository()
        source.connect(nearby = true)
        WatchEventProcessor(source, history).start(backgroundScope)
        source.emit(ack(id1, 1))
        runCurrent()
        assertEquals(listOf(id1), history.history().first().map { it.id })
        assertEquals(listOf(id1), source.storedIds)
    }

    @Test
    fun `a duplicate is marked stored too, so the Data Layer item is cleaned up`() = runTest {
        val source = FakeWatchLinkDataSource()
        val history = InMemoryHistoryRepository()
        source.connect(nearby = true)
        WatchEventProcessor(source, history).start(backgroundScope)
        source.emit(ack(id1, 1))
        source.emit(ack(id1, 1))
        runCurrent()
        assertEquals(1, history.history().first().size)
        assertEquals(listOf(id1, id1), source.storedIds)
    }

    @Test
    fun `a hello is cleared from the pending set but creates no history row`() = runTest {
        val source = FakeWatchLinkDataSource()
        val history = InMemoryHistoryRepository()
        source.connect(nearby = true)
        WatchEventProcessor(source, history).start(backgroundScope)
        source.emit(WatchEvent.HelloReceived(id2, 5, Hello(Side.WATCH, Protocol.VERSION, "0.1")))
        runCurrent()
        assertTrue(history.history().first().isEmpty())
        assertEquals(listOf(id2), source.storedIds)
    }

    @Test
    fun `an event whose row could not be stored is not marked stored and the next event still works`() = runTest {
        val source = FakeWatchLinkDataSource()
        val failing = object : HistoryRepository {
            val inner = InMemoryHistoryRepository()
            var fail = true

            override fun history(type: HistoryType?): Flow<List<HistoryEntry>> = inner.history(type)

            override suspend fun store(event: WatchEvent): StoreResult {
                if (fail) {
                    fail = false
                    error("disk full")
                }
                return inner.store(event)
            }
        }
        source.connect(nearby = true)
        WatchEventProcessor(source, failing).start(backgroundScope)
        source.emit(ack(id1, 1))
        source.emit(ack(id2, 2))
        runCurrent()
        assertEquals("the failed one stays in the Data Layer", listOf(id2), source.storedIds)
    }

    @Test
    fun `start publishes the phone hello once`() = runTest {
        val source = FakeWatchLinkDataSource()
        WatchEventProcessor(source, InMemoryHistoryRepository(), appVersion = "1.2").start(backgroundScope)
        runCurrent()
        val hello = source.publishedHellos.single()
        assertEquals(Side.PHONE, hello.side)
        assertEquals(Protocol.VERSION, hello.protocolVersion)
        assertEquals("1.2", hello.appVersion)
    }
}

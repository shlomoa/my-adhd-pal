package com.shlomoa.myadhdpal.wear.datalayer

import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.TransportListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedTransportTest {
    private class Writer : ItemWriter {
        val writes = ArrayList<String>()

        override fun put(path: String, envelope: Envelope) {
            writes += "put $path ${envelope.id}"
        }

        override fun delete(path: String) {
            writes += "delete $path"
        }
    }

    private class Recorder : TransportListener {
        val calls = ArrayList<String>()

        override fun onItemChanged(item: StoredItem) {
            calls += "changed ${item.path} ${item.envelope.id}"
        }

        override fun onItemDeleted(path: String) {
            calls += "deleted $path"
        }

        override fun onLinkStateChanged(state: LinkState) {
            calls += "state $state"
        }
    }

    private val writer = Writer()
    private val recorder = Recorder()
    private var state = LinkState.AWAY
    private val transport = CachedTransport(writer, localNodeId = "watch-1", observeLinkState = { state })
        .also { it.setListener(recorder) }

    private fun envelope(id: String) = Envelope(version = 1, id = id, timestamp = 1L, body = "{}")

    @Test
    fun `TC-W-110 the cache is empty before the first load and holds the loaded items after`() {
        assertTrue(transport.items().isEmpty())
        transport.load(listOf(StoredItem("/schedule", envelope("a"))))
        assertEquals(listOf(StoredItem("/schedule", envelope("a"))), transport.items())
    }

    @Test
    fun `TC-W-111 put updates the cache at once and a newer item replaces the older`() {
        transport.put("/hello/watch", envelope("a"))
        assertEquals(listOf(StoredItem("/hello/watch", envelope("a"))), transport.items())
        transport.put("/hello/watch", envelope("b"))
        assertEquals(listOf(StoredItem("/hello/watch", envelope("b"))), transport.items())
        assertEquals(listOf("put /hello/watch a", "put /hello/watch b"), writer.writes)
    }

    @Test
    fun `TC-W-112 delete removes the item from the cache and deletes it in the Data Layer`() {
        transport.put("/ack/x", envelope("a"))
        transport.delete("/ack/x")
        assertTrue(transport.items().isEmpty())
        assertEquals(listOf("put /ack/x a", "delete /ack/x"), writer.writes)
    }

    @Test
    fun `TC-W-113 a change by the other node is cached and reported, one by this node is not reported`() {
        transport.onDataChanged("/schedule", envelope("s1"), originNodeId = "phone-1")
        transport.onDataChanged("/ack/x", envelope("own"), originNodeId = "watch-1")
        assertEquals(listOf("changed /schedule s1"), recorder.calls)
        assertEquals(setOf("/schedule", "/ack/x"), transport.items().map { it.path }.toSet())
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun `TC-W-114 a deletion by the other node is removed and reported, one by this node is not reported`() {
        transport.load(listOf(StoredItem("/prompt/p", envelope("p")), StoredItem("/ack/x", envelope("x"))))
        transport.onDataDeleted("/prompt/p", originNodeId = "phone-1")
        transport.onDataDeleted("/ack/x", originNodeId = "watch-1")
        assertEquals(listOf("deleted /prompt/p"), recorder.calls)
        assertTrue(transport.items().isEmpty())
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun `TC-W-115 a load that finishes after a local change keeps the local change`() {
        transport.put("/hello/watch", envelope("new"))
        transport.put("/ack/gone", envelope("g"))
        transport.delete("/ack/gone")
        transport.load(
            listOf(
                StoredItem("/hello/watch", envelope("old")),
                StoredItem("/ack/gone", envelope("g")),
                StoredItem("/schedule", envelope("s"))
            )
        )
        val byPath = transport.items().associate { it.path to it.envelope.id }
        assertEquals(mapOf("/hello/watch" to "new", "/schedule" to "s"), byPath)
    }

    @Test
    fun `TC-W-116 the link state is read from the observation and a change reaches the listener once`() {
        assertEquals(LinkState.AWAY, transport.linkState)
        state = LinkState.NEARBY
        transport.refreshLinkState()
        transport.refreshLinkState()
        assertEquals(LinkState.NEARBY, transport.linkState)
        state = LinkState.RELAYED
        transport.refreshLinkState()
        assertEquals(listOf("state NEARBY", "state RELAYED"), recorder.calls)
    }
}

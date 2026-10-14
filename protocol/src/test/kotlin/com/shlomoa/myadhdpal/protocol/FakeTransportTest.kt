package com.shlomoa.myadhdpal.protocol

import com.shlomoa.myadhdpal.protocol.fake.FakeLink
import com.shlomoa.myadhdpal.protocol.fake.FakeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTransportTest {
    private val ids = (1..9).map { "123e4567-e89b-42d3-a456-42661417400$it" }

    private class Recorder : TransportListener {
        val events = mutableListOf<String>()
        val states = mutableListOf<LinkState>()

        override fun onItemChanged(item: StoredItem) {
            events += "put ${item.path} ${item.envelope.body}"
        }

        override fun onItemDeleted(path: String) {
            events += "delete $path"
        }

        override fun onLinkStateChanged(state: LinkState) {
            states += state
        }
    }

    private fun env(index: Int, body: String = "b$index") = Envelope(Protocol.MAJOR_VERSION, ids[index], index.toLong(), body)

    private fun connected(): Triple<FakeLink, Recorder, Recorder> {
        val link = FakeLink()
        val phone = Recorder()
        val watch = Recorder()
        link.phone.setListener(phone)
        link.watch.setListener(watch)
        return Triple(link, phone, watch)
    }

    private fun FakeNode.paths() = items().map { it.path }

    @Test
    fun `TC-S-072 an item sent while connected is received once and in order`() {
        val (link, phone, watch) = connected()
        link.connect(nearby = true)
        link.phone.put(Paths.prompt(ids[1]), env(1))
        link.phone.put(Paths.prompt(ids[2]), env(2))
        link.watch.put(Paths.ack(ids[1]), env(1, "ack"))
        assertEquals(listOf("put ${Paths.prompt(ids[1])} b1", "put ${Paths.prompt(ids[2])} b2"), watch.events)
        assertEquals(listOf("put ${Paths.ack(ids[1])} ack"), phone.events)
        assertEquals(listOf(Paths.prompt(ids[1]), Paths.prompt(ids[2])), link.watch.paths())
        // the writer keeps its own copy
        assertEquals(listOf(Paths.prompt(ids[1]), Paths.prompt(ids[2])), link.phone.paths().sorted())
    }

    @Test
    fun `TC-S-073 items sent while disconnected arrive on reconnect, in order`() {
        val (link, _, watch) = connected()
        link.disconnect()
        link.phone.put(Paths.prompt(ids[1]), env(1))
        link.phone.put(Paths.SCHEDULE, env(2))
        link.phone.put(Paths.prompt(ids[3]), env(3))
        assertTrue("nothing is delivered while away", watch.events.isEmpty())
        assertEquals("the writer sees its own items at once", 3, link.phone.items().size)
        assertTrue(link.watch.items().isEmpty())
        link.connect(nearby = false)
        assertEquals(
            listOf("put ${Paths.prompt(ids[1])} b1", "put ${Paths.SCHEDULE} b2", "put ${Paths.prompt(ids[3])} b3"),
            watch.events
        )
    }

    @Test
    fun `TC-S-074 a newer item at the same path replaces the older one`() {
        val (link, _, watch) = connected()
        link.disconnect()
        link.phone.put(Paths.SCHEDULE, env(1, "v1"))
        link.phone.put(Paths.SCHEDULE, env(2, "v2"))
        link.phone.put(Paths.SCHEDULE, env(3, "v3"))
        link.connect(nearby = true)
        assertEquals("only the newest version is delivered", listOf("put ${Paths.SCHEDULE} v3"), watch.events)
        assertEquals(listOf("v3"), link.watch.items().map { it.envelope.body })
        // while connected each write is delivered, and the stored item is the newest
        link.phone.put(Paths.SCHEDULE, env(4, "v4"))
        assertEquals(listOf("put ${Paths.SCHEDULE} v3", "put ${Paths.SCHEDULE} v4"), watch.events)
        assertEquals(listOf("v4"), link.watch.items().map { it.envelope.body })
    }

    @Test
    fun `TC-S-075 a deleted item is not redelivered after a reconnect`() {
        val (link, phone, watch) = connected()
        link.connect(nearby = true)
        link.watch.put(Paths.ack(ids[1]), env(1))
        link.phone.delete(Paths.ack(ids[1]))
        assertTrue(link.phone.items().isEmpty() && link.watch.items().isEmpty())
        val before = phone.events.size + watch.events.size
        repeat(3) {
            link.disconnect()
            link.connect(nearby = true)
        }
        assertEquals("no event after the reconnects", before, phone.events.size + watch.events.size)
        assertTrue(link.phone.items().isEmpty() && link.watch.items().isEmpty())
    }

    @Test
    fun `TC-S-075 a delete while disconnected reaches the peer on reconnect, and an undelivered item is never delivered`() {
        val (link, _, watch) = connected()
        link.connect(nearby = true)
        link.phone.put(Paths.prompt(ids[1]), env(1))
        link.disconnect()
        link.phone.delete(Paths.prompt(ids[1]))
        link.phone.put(Paths.prompt(ids[2]), env(2))
        link.phone.delete(Paths.prompt(ids[2]))
        assertEquals("the watch still has the first prompt", listOf(Paths.prompt(ids[1])), link.watch.paths())
        link.connect(nearby = true)
        assertEquals(
            listOf("put ${Paths.prompt(ids[1])} b1", "delete ${Paths.prompt(ids[1])}"),
            watch.events
        )
        assertTrue(link.watch.items().isEmpty())
    }

    @Test
    fun `TC-S-076 repeated disconnect and reconnect gives no duplicates and no loss`() {
        val (link, phone, watch) = connected()
        val sent = mutableListOf<String>()
        link.connect(nearby = true)
        for (round in 1..4) {
            link.disconnect()
            for (n in 1..2) {
                val index = (round - 1) * 2 + n
                if (index > 8) break
                val path = Paths.prompt(ids[index])
                link.phone.put(path, env(index))
                sent += "put $path b$index"
            }
            link.connect(nearby = round % 2 == 0)
            link.disconnect()
            link.connect(nearby = true)
        }
        assertEquals(sent, watch.events)
        assertTrue(phone.events.isEmpty())
        assertEquals(8, link.watch.items().size)
    }

    @Test
    fun `the link state is reported to the node listeners and to the node`() {
        val (link, phone, watch) = connected()
        assertEquals(LinkState.AWAY, link.phone.linkState)
        link.connect(nearby = true)
        link.connect(nearby = false)
        link.setState(LinkState.NO_WATCH_APP)
        link.setState(LinkState.INCOMPATIBLE)
        link.disconnect()
        val expected = listOf(LinkState.NEARBY, LinkState.RELAYED, LinkState.NO_WATCH_APP, LinkState.INCOMPATIBLE, LinkState.AWAY)
        assertEquals(expected, phone.states)
        assertEquals(expected, watch.states)
        assertEquals(LinkState.AWAY, link.watch.linkState)
        assertEquals(
            setOf("NEARBY", "RELAYED", "AWAY", "NO_WATCH_APP", "INCOMPATIBLE"),
            LinkState.entries.map { it.name }.toSet()
        )
    }

    @Test
    fun `items do not flow when there is no watch app`() {
        val (link, _, watch) = connected()
        link.setState(LinkState.NO_WATCH_APP)
        link.phone.put(Paths.SCHEDULE, env(1))
        assertTrue(watch.events.isEmpty())
        link.connect(nearby = true)
        assertEquals(1, watch.events.size)
    }
}

package com.shlomoa.myadhdpal.wear.link

import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Side
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.TransportListener
import com.shlomoa.myadhdpal.protocol.fake.FakeLink
import com.shlomoa.myadhdpal.wear.R
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkClientTest {
    private val link = FakeLink()
    private var now = Instant.parse("2026-10-14T08:00:00Z")
    private val ids = ArrayDeque(listOf("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"))

    private fun client(transport: LinkTransport = link.watch, lastSyncAt: Instant? = null) =
        LinkClient(transport, appVersion = "0.1.0", newId = {
            ids.removeFirst()
        }, now = { now }, lastSyncAt = lastSyncAt)
            .also { transport.setListener(it) }

    @Test
    fun `TC-W-001 the home screen shows Nearby when the link is nearby`() {
        val client = client().apply { start() }
        link.connect(nearby = true)
        assertEquals(LinkState.NEARBY, client.view.state)
        assertEquals(R.string.link_nearby, client.view.state.watchLabelRes())
    }

    @Test
    fun `TC-W-002 the home screen shows Relayed when the link is relayed`() {
        val client = client().apply { start() }
        link.connect(nearby = false)
        assertEquals(LinkState.RELAYED, client.view.state)
        assertEquals(R.string.link_relayed, client.view.state.watchLabelRes())
    }

    @Test
    fun `TC-W-003 the home screen shows Away when the phone is not connected`() {
        val client = client().apply { start() }
        link.connect(nearby = true)
        link.disconnect()
        assertEquals(LinkState.AWAY, client.view.state)
        assertEquals(R.string.link_away, client.view.state.watchLabelRes())
    }

    @Test
    fun `TC-W-004 the home screen shows Incompatible when the transport reports it`() {
        val client = client().apply { start() }
        link.setState(LinkState.INCOMPATIBLE)
        assertEquals(LinkState.INCOMPATIBLE, client.view.state)
        assertEquals(R.string.link_incompatible, client.view.state.watchLabelRes())
    }

    @Test
    fun `TC-W-005 the last sync time is shown and updated when a schedule is applied`() {
        val earlier = Instant.parse("2026-10-13T20:15:00Z")
        val client = client(lastSyncAt = earlier).apply { start() }
        assertEquals(earlier, client.view.lastSyncAt)
        client.onScheduleApplied(now)
        assertEquals(now, client.view.lastSyncAt)
    }

    @Test
    fun `TC-W-005 without a sync the last sync time is empty`() {
        assertNull(client().apply { start() }.view.lastSyncAt)
    }

    @Test
    fun `TC-W-006 the view refreshes on a link change callback and on a periodic re-read`() {
        val silent = SilentTransport(link.watch)
        val client = client(silent).apply { start() }
        val seen = mutableListOf<LinkView>()
        client.setOnChange { seen += it }

        silent.hidden = LinkState.RELAYED
        assertEquals(LinkState.AWAY, client.view.state)
        client.refresh()
        assertEquals(LinkState.RELAYED, client.view.state)

        client.onLinkStateChanged(LinkState.NEARBY)
        assertEquals(listOf(LinkState.RELAYED, LinkState.NEARBY), seen.map { it.state })
    }

    @Test
    fun `TC-W-007 start publishes hello watch with the protocol and app version and overwrites the old one`() {
        client().start()
        now = now.plusSeconds(60)
        client().start()

        val hellos = link.watch.items().filter { it.path == Paths.HELLO_WATCH }
        assertEquals(1, hellos.size)
        val decoded = Codec.decode(Paths.HELLO_WATCH, hellos.single().envelope)
        assertTrue(decoded is DecodeResult.Ok)
        val item = (decoded as DecodeResult.Ok).item
        assertEquals(Hello(Side.WATCH, Protocol.VERSION, "0.1.0"), item.message)
        assertEquals(now.toEpochMilli(), item.timestamp)
        assertEquals("22222222-2222-4222-8222-222222222222", item.id)
    }

    /** A transport whose state changes are not announced, like the periodic re-read case. */
    private class SilentTransport(private val inner: LinkTransport) : LinkTransport by inner {
        var hidden: LinkState? = null
        override val linkState: LinkState get() = hidden ?: inner.linkState
        override fun put(path: String, envelope: Envelope) = inner.put(path, envelope)
        override fun items(): List<StoredItem> = inner.items()
        override fun setListener(listener: TransportListener?) = inner.setListener(listener)
    }
}

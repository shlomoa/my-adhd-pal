package com.shlomoa.myadhdpal.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkStateTest {
    private val near = NodeObservation("watch-1", isNearby = true)
    private val far = NodeObservation("watch-1", isNearby = false)
    private val other = NodeObservation("watch-2", isNearby = false)
    private val compatible = Protocol.VERSION

    private fun derive(connected: List<NodeObservation>, withApp: List<NodeObservation>, hello: String?) =
        LinkStateLogic.derive(LinkObservation(connected, withApp, hello))

    @Test
    fun `TC-S-060 Nearby when the app is on a directly connected watch with a compatible hello`() {
        assertEquals(LinkState.NEARBY, derive(listOf(near), listOf(near), compatible))
        assertEquals(LinkState.NEARBY, derive(listOf(near), listOf(near), "1.0"))
        assertEquals(LinkState.NEARBY, derive(listOf(near), listOf(near), "1.9.3"))
    }

    @Test
    fun `TC-S-061 Relayed when the app is on a watch connected only through the relay`() {
        assertEquals(LinkState.RELAYED, derive(listOf(far), listOf(far), compatible))
        assertEquals(LinkState.RELAYED, derive(listOf(far), listOf(far), "1.1"))
    }

    @Test
    fun `TC-S-062 Away when no watch is connected`() {
        assertEquals(LinkState.AWAY, derive(emptyList(), emptyList(), compatible))
        assertEquals(LinkState.AWAY, derive(emptyList(), emptyList(), null))
        assertEquals(LinkState.AWAY, derive(emptyList(), emptyList(), "2.0"))
    }

    @Test
    fun `TC-S-063 No watch app when a watch is connected without the app capability`() {
        assertEquals(LinkState.NO_WATCH_APP, derive(listOf(near), emptyList(), null))
        assertEquals(LinkState.NO_WATCH_APP, derive(listOf(far), emptyList(), compatible))
        assertEquals(LinkState.NO_WATCH_APP, derive(listOf(near, other), emptyList(), null))
    }

    @Test
    fun `TC-S-064 Incompatible when the hello has an unsupported major version`() {
        assertEquals(LinkState.INCOMPATIBLE, derive(listOf(near), listOf(near), "2.0"))
        assertEquals(LinkState.INCOMPATIBLE, derive(listOf(far), listOf(far), "0.9"))
        assertEquals(LinkState.INCOMPATIBLE, derive(listOf(near), listOf(near), "99.1.2"))
    }

    @Test
    fun `TC-S-064 Incompatible when the hello version is malformed`() {
        for (text in listOf("", "abc", "1", "1.", ".1", "1.x", "-1.0", "1.0.0.0", " 1.1")) {
            assertEquals("hello '$text'", LinkState.INCOMPATIBLE, derive(listOf(near), listOf(near), text))
        }
    }

    @Test
    fun `a missing hello does not hide a watch that has the app`() {
        assertEquals(LinkState.NEARBY, derive(listOf(near), listOf(near), null))
        assertEquals(LinkState.RELAYED, derive(listOf(far), listOf(far), null))
    }

    @Test
    fun `TC-S-066 Incompatible overrides Nearby and Relayed`() {
        assertEquals(LinkState.INCOMPATIBLE, derive(listOf(near), listOf(near), "2.0"))
        assertEquals(LinkState.INCOMPATIBLE, derive(listOf(far), listOf(far), "2.0"))
        assertEquals(LinkState.NEARBY, derive(listOf(near), listOf(near), "1.0"))
    }

    @Test
    fun `TC-S-066 No watch app overrides Away and Incompatible when a watch is connected`() {
        assertEquals(LinkState.NO_WATCH_APP, derive(listOf(far), emptyList(), "2.0"))
        assertEquals(LinkState.NO_WATCH_APP, derive(listOf(near), emptyList(), "garbage"))
    }

    @Test
    fun `a watch with the app that the node list does not yet show is not Away`() {
        // The two Data Layer queries are not atomic; the capability list alone proves a reachable watch.
        assertEquals(LinkState.NEARBY, derive(emptyList(), listOf(near), compatible))
        assertEquals(LinkState.RELAYED, derive(emptyList(), listOf(far), compatible))
    }

    @Test
    fun `with several watches the one with the app and the nearest link decides`() {
        assertEquals(LinkState.NEARBY, derive(listOf(other, near), listOf(other, near), compatible))
        assertEquals(LinkState.RELAYED, derive(listOf(near, other), listOf(other), compatible))
        assertEquals(LinkState.NEARBY, derive(listOf(near, other), listOf(near), compatible))
    }

    @Test
    fun `TC-S-065 every combination of inputs maps to exactly one of the five states`() {
        val nodeLists = listOf(emptyList(), listOf(near), listOf(far), listOf(near, other), listOf(far, other))
        val hellos = listOf(null, "", "garbage", "0.9", "1.0", "1.1", "1.9.3", "2.0")
        val seen = mutableSetOf<LinkState>()
        for (connected in nodeLists) {
            for (withApp in nodeLists) {
                for (hello in hellos) {
                    val state = derive(connected, withApp, hello)
                    seen += state
                    val expected =
                        when {
                            connected.isEmpty() && withApp.isEmpty() -> LinkState.AWAY

                            withApp.isEmpty() -> LinkState.NO_WATCH_APP

                            hello != null && Compatibility.ofHello(hello) is Compatibility.Incompatible ->
                                LinkState.INCOMPATIBLE

                            withApp.any { it.isNearby } -> LinkState.NEARBY

                            else -> LinkState.RELAYED
                        }
                    assertEquals("connected=$connected withApp=$withApp hello=$hello", expected, state)
                }
            }
        }
        assertEquals(LinkState.entries.toSet(), seen)
    }

    @Test
    fun `the function is pure and repeatable`() {
        val observation = LinkObservation(listOf(near), listOf(near), compatible)
        assertEquals(LinkStateLogic.derive(observation), LinkStateLogic.derive(observation))
        assertTrue(LinkObservation(emptyList(), emptyList(), null) == LinkObservation(emptyList(), emptyList(), null))
    }

    @Test
    fun `the watch app capability name is fixed`() {
        assertEquals("adhd_pal_watch", Capabilities.WATCH_APP)
    }
}

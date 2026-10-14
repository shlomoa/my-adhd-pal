package com.shlomoa.myadhdpal.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityTest {
    private val id = "123e4567-e89b-42d3-a456-426614174000"
    private val body = """{"key":"calm","at":42}"""

    private fun envelope(major: Int) = Envelope(major, id, 1L, body)

    @Test
    fun `TC-S-056 an equal major version is accepted`() {
        assertEquals(Compatibility.Compatible, Compatibility.ofMajor(Protocol.MAJOR_VERSION))
        assertEquals(Compatibility.Compatible, Compatibility.ofHello(Protocol.VERSION))
        val decoded = Codec.decodeIfCompatible(Paths.selection(id), envelope(Protocol.MAJOR_VERSION))
        assertEquals(DecodeResult.Ok(Item(id, 1L, Selection("calm", 42))), decoded)
    }

    @Test
    fun `TC-S-057 a higher or lower major version is Incompatible and the item is ignored`() {
        for (major in listOf(Protocol.MAJOR_VERSION + 1, Protocol.MAJOR_VERSION - 1, 0, -1, 99, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertTrue("major $major", Compatibility.ofMajor(major) is Compatibility.Incompatible)
            val decoded = Codec.decodeIfCompatible(Paths.selection(id), envelope(major))
            assertTrue("major $major -> $decoded", decoded is DecodeResult.Failure)
        }
        assertTrue(Compatibility.ofHello("2.0") is Compatibility.Incompatible)
        assertTrue(Compatibility.ofHello("0.9") is Compatibility.Incompatible)
    }

    @Test
    fun `TC-S-058 minor and patch differences are accepted`() {
        for (version in listOf("1.0", "1.1", "1.7", "1.99", "1.0.3", "1.12.0", "1.0.999")) {
            assertEquals("hello $version", Compatibility.Compatible, Compatibility.ofHello(version))
        }
        assertEquals(ProtocolVersion(1, 7), ProtocolVersion.parse("1.7"))
        assertEquals(ProtocolVersion(1, 7), ProtocolVersion.parse("1.7.4"))
    }

    @Test
    fun `TC-S-059 a missing or malformed hello version is Incompatible and never throws`() {
        val bad = listOf(
            null,
            "",
            " ",
            "abc",
            "1",
            "1.",
            ".1",
            "1.x",
            "x.1",
            "-1.0",
            "1.-1",
            "1..2",
            "1.0.0.0",
            "1.0-beta",
            " 1.0",
            "1.0 ",
            "99999999999999999999.0",
            "1.99999999999999999999",
            "1,0"
        )
        for (version in bad) {
            assertTrue("hello '$version'", Compatibility.ofHello(version) is Compatibility.Incompatible)
            assertNull("parse '$version'", ProtocolVersion.parse(version))
        }
    }
}

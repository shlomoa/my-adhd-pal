package com.shlomoa.myadhdpal.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PathsTest {
    private val id = "123e4567-e89b-42d3-a456-426614174000"

    @Test
    fun `TC-S-042 path builders produce exactly the paths of the spec`() {
        assertEquals("/schedule", Paths.SCHEDULE)
        assertEquals("/prompt/$id", Paths.prompt(id))
        assertEquals("/ack/$id", Paths.ack(id))
        assertEquals("/selection/$id", Paths.selection(id))
        assertEquals("/alert/$id", Paths.alert(id))
        assertEquals("/hello/phone", Paths.HELLO_PHONE)
        assertEquals("/hello/watch", Paths.HELLO_WATCH)
        assertEquals("/debug/biometrics", Paths.DEBUG_BIOMETRICS)
    }

    @Test
    fun `TC-S-043 path parsers return the kind and id of every builder output`() {
        assertEquals(ItemPath.Schedule, Paths.parse(Paths.SCHEDULE))
        assertEquals(ItemPath.Prompt(id), Paths.parse(Paths.prompt(id)))
        assertEquals(ItemPath.Ack(id), Paths.parse(Paths.ack(id)))
        assertEquals(ItemPath.Selection(id), Paths.parse(Paths.selection(id)))
        assertEquals(ItemPath.Alert(id), Paths.parse(Paths.alert(id)))
        assertEquals(ItemPath.Hello(Side.PHONE), Paths.parse(Paths.HELLO_PHONE))
        assertEquals(ItemPath.Hello(Side.WATCH), Paths.parse(Paths.HELLO_WATCH))
        assertEquals(ItemPath.DebugBiometrics, Paths.parse(Paths.DEBUG_BIOMETRICS))
    }

    @Test
    fun `TC-S-043 unknown paths and empty ids return null`() {
        listOf("", "/", "/unknown", "/prompt", "/prompt/", "/ack/", "/hello", "/hello/tablet", "schedule", "/Schedule")
            .forEach { assertNull("path '$it'", Paths.parse(it)) }
    }

    @Test
    fun `TC-S-044 ids with a slash, whitespace or non-UUID text are rejected`() {
        val bad = listOf(
            "a/b",
            "$id ",
            " $id",
            "not-a-uuid",
            "123e4567-e89b-42d3-a456-42661417400",
            "123E4567-E89B-42D3-A456-426614174000",
            "123e4567e89b42d3a456426614174000",
            "../schedule",
            ""
        )
        for (bad1 in bad) {
            assertNull("parse prompt '$bad1'", Paths.parse("/prompt/$bad1"))
            assertThrows("build '$bad1'", IllegalArgumentException::class.java) { Paths.prompt(bad1) }
            assertThrows("build '$bad1'", IllegalArgumentException::class.java) { Paths.ack(bad1) }
            assertThrows("build '$bad1'", IllegalArgumentException::class.java) { Paths.selection(bad1) }
            assertThrows("build '$bad1'", IllegalArgumentException::class.java) { Paths.alert(bad1) }
        }
    }

    @Test
    fun `TC-S-044 a trailing slash or extra segment is rejected`() {
        assertNull(Paths.parse("/schedule/"))
        assertNull(Paths.parse("/prompt/$id/"))
        assertNull(Paths.parse("/prompt/$id/extra"))
        assertNull(Paths.parse("/hello/phone/"))
        assertNull(Paths.parse("/debug/biometrics/"))
    }
}

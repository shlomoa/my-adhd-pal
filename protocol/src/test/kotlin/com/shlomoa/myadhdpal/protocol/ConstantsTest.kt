package com.shlomoa.myadhdpal.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstantsTest {
    @Test
    fun `TC-S-045 item keys and the protocol major version equal the spec values`() {
        assertEquals("v", ItemKeys.VERSION)
        assertEquals("id", ItemKeys.ID)
        assertEquals("ts", ItemKeys.TIMESTAMP)
        assertEquals("body", ItemKeys.BODY)
        assertEquals(1, Protocol.MAJOR_VERSION)
        assertEquals("1.2", Protocol.VERSION)
    }
}

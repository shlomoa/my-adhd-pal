package com.shlomoa.myadhdpal.wear.datalayer

import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.ItemKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemMappingTest {
    private val envelope = Envelope(version = 1, id = "11111111-1111-4111-8111-111111111111", timestamp = 42L, body = "{}")

    @Test
    fun `TC-W-117 the mapping round trips the four keys`() {
        val fields = envelope.toFields()
        assertEquals(setOf(ItemKeys.VERSION, ItemKeys.ID, ItemKeys.TIMESTAMP, ItemKeys.BODY), fields.keys)
        assertEquals(envelope, envelopeOf(fields))
    }

    @Test
    fun `TC-W-117 an item with a missing or mistyped key is rejected`() {
        val good = envelope.toFields()
        assertNull(envelopeOf(good - ItemKeys.BODY))
        assertNull(envelopeOf(good - ItemKeys.VERSION))
        assertNull(envelopeOf(good + (ItemKeys.TIMESTAMP to "42")))
        assertNull(envelopeOf(good + (ItemKeys.VERSION to 1L)))
        assertNull(envelopeOf(emptyMap()))
    }

    @Test
    fun `TC-W-119 prompts, acks, selections and alerts are urgent, hello and schedule are not`() {
        val id = "11111111-1111-4111-8111-111111111111"
        listOf("/prompt/$id", "/ack/$id", "/selection/$id", "/alert/$id").forEach { assertTrue(it, isUrgent(it)) }
        listOf("/hello/watch", "/hello/phone", "/schedule", "/debug/biometrics", "/ack/bad").forEach {
            assertFalse(it, isUrgent(it))
        }
    }
}

package com.shlomoa.myadhdpal.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protocol 1.3 (issue 55 decision): the schedule no longer carries absolute heart-rate thresholds. */
class Protocol13Test {
    private val id = "123e4567-e89b-42d3-a456-426614174001"
    private val ts = 1_700_000_000_000L

    private fun schedule(alerts: AlertSettings) = Schedule(2, emptyList(), emptyList(), SnoozeSettings(10, 3), alerts)

    @Test
    fun `TC-S-180 an encoded schedule has no thresholds key`() {
        val encoded = Codec.encode(id, ts, schedule(AlertSettings(cooldownSeconds = 300, offsetBpm = 40)))
        assertFalse(encoded.body, encoded.body.contains("thresholds"))
        assertTrue(encoded.body, encoded.body.contains("\"offsetBpm\":40"))
    }

    @Test
    fun `TC-S-181 a 1_2 schedule body that still has a thresholds list decodes and the list is ignored`() {
        val body = """{"version":4,"reminders":[],"checkpoints":[],"snooze":{"minutes":10,"maxCount":3},""" +
            """"alerts":{"thresholds":[{"metric":"HEART_RATE","direction":"ABOVE","value":150.0}],""" +
            """"cooldownSeconds":600,"offsetBpm":35,"initialBpm":115}}"""
        val decoded = Codec.decode(Paths.SCHEDULE, Envelope(1, id, ts, body)) as DecodeResult.Ok
        val alerts = (decoded.item.message as Schedule).alerts
        assertEquals(AlertSettings(cooldownSeconds = 600, offsetBpm = 35, initialBpm = 115), alerts)
    }

    @Test
    fun `TC-S-182 the protocol version is 1_3 and peers 1_0 to 1_2 stay compatible`() {
        assertEquals("1.3", Protocol.VERSION)
        for (peer in listOf("1.0", "1.1", "1.2", Protocol.VERSION)) {
            assertEquals(peer, Compatibility.Compatible, Compatibility.ofHello(peer))
        }
        assertEquals(Compatibility.Incompatible, Compatibility.ofHello("2.0"))
    }
}

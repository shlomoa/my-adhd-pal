package com.shlomoa.myadhdpal.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protocol 1.2 (issue 168): alert level and episode, relative threshold, end of episode, watch status. */
class Protocol12Test {
    private val id = "123e4567-e89b-42d3-a456-426614174000"
    private val episode = "223e4567-e89b-42d3-a456-426614174001"
    private val ts = 1_760_000_000_000L

    private fun roundTrip(message: Message): Item {
        val decoded = Codec.decode(Paths.pathFor(id, message), Codec.encode(id, ts, message))
        assertTrue("decode failed: $decoded", decoded is DecodeResult.Ok)
        return (decoded as DecodeResult.Ok).item
    }

    @Test
    fun `TC-S-150 alert level defaults to 1, accepts 1 and 2, rejects other values`() {
        assertEquals(1, Alert(Metric.HEART_RATE, 150.0, 140.0, ts).level)
        assertEquals(2, Alert(Metric.HEART_RATE, 150.0, 140.0, ts, level = 2).level)
        for (bad in listOf(0, 3, -1, 99)) {
            assertThrows("level $bad", IllegalArgumentException::class.java) {
                Alert(Metric.HEART_RATE, 150.0, 140.0, ts, level = bad)
            }
        }
        val alert = Alert(Metric.HEART_RATE, 160.0, 140.0, ts, level = 2, episodeId = episode)
        assertEquals(alert, roundTrip(alert).message)
    }

    @Test
    fun `TC-S-150 a 1_1 alert body without level or episode decodes as level 1`() {
        val body = """{"metric":"HEART_RATE","value":150.0,"threshold":140.0,"at":$ts}"""
        val decoded = Codec.decode(Paths.alert(id), Envelope(1, id, ts, body)) as DecodeResult.Ok
        assertEquals(Alert(Metric.HEART_RATE, 150.0, 140.0, ts, level = 1, episodeId = null), decoded.item.message)
    }

    @Test
    fun `TC-S-151 alert episode id is a canonical UUID, may be absent and is not written when absent`() {
        assertNull(Alert(Metric.HEART_RATE, 1.0, 1.0, 1).episodeId)
        for (bad in listOf("", " ", "not-a-uuid", episode.uppercase(), "$episode/x")) {
            assertThrows("episode '$bad'", IllegalArgumentException::class.java) {
                Alert(Metric.HEART_RATE, 1.0, 1.0, 1, episodeId = bad)
            }
        }
        val body = Codec.encodeBody(Alert(Metric.HEART_RATE, 1.0, 1.0, 1))
        assertFalse(body, body.contains("episodeId"))
    }

    @Test
    fun `TC-S-152 alert settings carry the offset and the initial threshold with the decided defaults`() {
        val settings = AlertSettings(emptyList(), cooldownSeconds = AlertRules.DEFAULT_COOLDOWN_SECONDS)
        assertEquals(30, settings.offsetBpm)
        assertEquals(110, settings.initialBpm)
        assertEquals(300, AlertRules.DEFAULT_COOLDOWN_SECONDS)
        assertEquals(120, AlertRules.ESCALATION_REPEAT_SECONDS)
        assertEquals(6, AlertRules.MAX_LEVEL2_REPEATS)
        val custom = AlertSettings(emptyList(), 300, offsetBpm = 40, initialBpm = 120)
        assertEquals(custom, roundTrip(schedule(custom)).let { (it.message as Schedule).alerts })
    }

    @Test
    fun `TC-S-152 offset is 20 to 50 and the initial threshold 30 to 220`() {
        for (ok in listOf(20, 30, 50)) AlertSettings(emptyList(), 300, offsetBpm = ok)
        for (bad in listOf(19, 51, 0, -5)) {
            assertThrows("offset $bad", IllegalArgumentException::class.java) {
                AlertSettings(emptyList(), 300, offsetBpm = bad)
            }
        }
        for (ok in listOf(30, 110, 220)) AlertSettings(emptyList(), 300, initialBpm = ok)
        for (bad in listOf(29, 221, 0)) {
            assertThrows("initial $bad", IllegalArgumentException::class.java) {
                AlertSettings(emptyList(), 300, initialBpm = bad)
            }
        }
    }

    @Test
    fun `TC-S-152 a 1_1 schedule body without the new alert fields still decodes with the defaults`() {
        val body = """{"version":4,"reminders":[],"checkpoints":[],"snooze":{"minutes":10,"maxCount":3},""" +
            """"alerts":{"thresholds":[],"cooldownSeconds":600}}"""
        val decoded = Codec.decode(Paths.SCHEDULE, Envelope(1, id, ts, body)) as DecodeResult.Ok
        val alerts = (decoded.item.message as Schedule).alerts
        assertEquals(30, alerts.offsetBpm)
        assertEquals(110, alerts.initialBpm)
        assertEquals(600, alerts.cooldownSeconds)
    }

    @Test
    fun `TC-S-153 episode end round trip, invalid episode id and negative time are rejected`() {
        val end = EpisodeEnd(episode, at = ts)
        assertEquals(end, roundTrip(end).message)
        assertThrows(IllegalArgumentException::class.java) { EpisodeEnd("nope", ts) }
        assertThrows(IllegalArgumentException::class.java) { EpisodeEnd(episode, -1) }
        val withExtra = """{"episodeId":"$episode","at":$ts,"future":true}"""
        val decoded = Codec.decode(Paths.episodeEnd(id), Envelope(1, id, ts, withExtra)) as DecodeResult.Ok
        assertEquals(end, decoded.item.message)
    }

    @Test
    fun `TC-S-154 the episode end path builds, parses and rejects bad ids and shapes`() {
        assertEquals("/episode-end/$id", Paths.episodeEnd(id))
        assertEquals(ItemPath.EpisodeEnd(id), Paths.parse(Paths.episodeEnd(id)))
        assertEquals(Paths.episodeEnd(id), Paths.pathFor(id, EpisodeEnd(episode, ts)))
        listOf(
            "/episode-end",
            "/episode-end/",
            "/episode-end/x",
            "/episode-end/$id/",
            "/episode-end/$id/extra",
            "/episodeend/$id"
        )
            .forEach { assertNull("path '$it'", Paths.parse(it)) }
        assertThrows(IllegalArgumentException::class.java) { Paths.episodeEnd("bad") }
    }

    @Test
    fun `TC-S-155 watch status round trip with and without optional numbers, unknown permission is invalid`() {
        val full = WatchStatus(baselineBpm = 58, thresholdBpm = 88, heartRatePermission = HeartRatePermission.GRANTED)
        assertEquals(full, roundTrip(full).message)
        val minimal = WatchStatus(heartRatePermission = HeartRatePermission.UNKNOWN)
        assertEquals(minimal, roundTrip(minimal).message)
        val body = Codec.encodeBody(minimal)
        assertFalse(body, body.contains("baselineBpm"))
        val bad = """{"heartRatePermission":"MAYBE"}"""
        assertTrue(Codec.decode(Paths.STATUS_WATCH, Envelope(1, id, ts, bad)) is DecodeResult.Failure)
        assertEquals("/status/watch", Paths.STATUS_WATCH)
        assertEquals(ItemPath.WatchStatus, Paths.parse(Paths.STATUS_WATCH))
        assertEquals(Paths.STATUS_WATCH, Paths.pathFor(id, minimal))
        assertThrows(IllegalArgumentException::class.java) {
            WatchStatus(baselineBpm = 10, heartRatePermission = HeartRatePermission.GRANTED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WatchStatus(thresholdBpm = 300, heartRatePermission = HeartRatePermission.GRANTED)
        }
    }

    @Test
    fun `TC-S-156 the protocol version is 1_2, the major is unchanged and 1_1 peers stay compatible`() {
        assertEquals("1.2", Protocol.VERSION)
        assertEquals(1, Protocol.MAJOR_VERSION)
        assertEquals(Compatibility.Compatible, Compatibility.ofHello("1.1"))
        assertEquals(Compatibility.Compatible, Compatibility.ofHello(Protocol.VERSION))
        val decoded = Codec.decodeIfCompatible(Paths.episodeEnd(id), Codec.encode(id, ts, EpisodeEnd(episode, ts)))
        assertTrue(decoded is DecodeResult.Ok)
    }

    private fun schedule(alerts: AlertSettings) = Schedule(1, emptyList(), emptyList(), SnoozeSettings(10, 3), alerts)
}

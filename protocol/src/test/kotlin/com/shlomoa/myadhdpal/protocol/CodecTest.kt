package com.shlomoa.myadhdpal.protocol

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTest {
    private val id = "123e4567-e89b-42d3-a456-426614174000"
    private val ts = 1_760_000_000_000L

    private fun roundTrip(message: Message): Item {
        val envelope = Codec.encode(id, ts, message)
        val decoded = Codec.decode(Paths.pathFor(id, message), envelope)
        assertTrue("decode failed: $decoded", decoded is DecodeResult.Ok)
        return (decoded as DecodeResult.Ok).item
    }

    private val schedule = Schedule(
        version = 7,
        reminders = listOf(
            Reminder("r1", "Take medication", 8 * 60, listOf(1, 2, 3, 4, 5)),
            Reminder("r2", "Stand up", 15 * 60 + 30, listOf(1, 2, 3, 4, 5, 6, 7))
        ),
        checkpoints = listOf(Checkpoint("c1", "Midday check", 12 * 60)),
        snooze = SnoozeSettings(minutes = 10, maxCount = 3),
        alerts = AlertSettings(
            thresholds = listOf(Threshold(Metric.HEART_RATE, Direction.ABOVE, 150.0)),
            cooldownSeconds = 600
        )
    )

    @Test
    fun `TC-S-046 schedule round trip keeps thresholds, cooldown and version`() {
        val item = roundTrip(schedule)
        assertEquals(Item(id, ts, schedule), item)
        assertEquals(7L, (item.message as Schedule).version)
    }

    @Test
    fun `TC-S-047 prompt round trip with and without expiry, one to n options`() {
        val one = Prompt("Drink water", listOf(PromptOption("done", "Done")), expiresAt = null)
        val many = Prompt(
            "Mood",
            (1..Limits.MAX_OPTIONS).map { PromptOption("k$it", "Option $it") },
            expiresAt = ts + 60_000
        )
        assertEquals(one, roundTrip(one).message)
        assertEquals(many, roundTrip(many).message)
    }

    @Test
    fun `TC-S-048 acknowledgement round trip for each action`() {
        AckAction.entries.forEach {
            val ack = Acknowledgement(it, at = ts - 5)
            assertEquals(ack, roundTrip(ack).message)
        }
        assertEquals(
            setOf("DONE", "SNOOZE", "START_2_MIN", "DISMISS"),
            AckAction.entries.map { it.name }.toSet()
        )
    }

    @Test
    fun `TC-S-048 acknowledgement round trip with the reminder it answers and the occurrence time`() {
        val ack = Acknowledgement(AckAction.SNOOZE, at = ts - 5, reminderId = "r1", occurrenceAt = ts - 60_000)
        assertEquals(ack, roundTrip(ack).message)
        val onlyReminder = Acknowledgement(AckAction.DONE, at = ts, reminderId = "r1")
        assertEquals(onlyReminder, roundTrip(onlyReminder).message)
        assertEquals(null, (roundTrip(Acknowledgement(AckAction.DONE, ts)).message as Acknowledgement).reminderId)
    }

    @Test
    fun `TC-S-048 an acknowledgement without the new fields (protocol 1_0) still decodes`() {
        val body = """{"action":"DONE","at":42}"""
        val decoded = Codec.decode(Paths.ack(id), Envelope(Protocol.MAJOR_VERSION, id, ts, body))
        assertEquals(DecodeResult.Ok(Item(id, ts, Acknowledgement(AckAction.DONE, 42))), decoded)
    }

    @Test
    fun `TC-S-048 absent optional fields are not written, so a 1_0 reader sees the old body`() {
        val body = Codec.encodeBody(Acknowledgement(AckAction.DONE, 42))
        assertEquals("""{"action":"DONE","at":42}""", body)
        val full = Codec.encodeBody(Acknowledgement(AckAction.DONE, 42, "r1", 41))
        assertEquals("""{"action":"DONE","at":42,"reminderId":"r1","occurrenceAt":41}""", full)
    }

    @Test
    fun `TC-S-053 an acknowledgement with a blank or too long reminder id or a negative occurrence is invalid`() {
        val tooLong = "r".repeat(Limits.MAX_KEY_LENGTH + 1)
        val bodies = listOf(
            """{"action":"DONE","at":1,"reminderId":""}""",
            """{"action":"DONE","at":1,"reminderId":"  "}""",
            """{"action":"DONE","at":1,"reminderId":"$tooLong"}""",
            """{"action":"DONE","at":1,"occurrenceAt":-1}""",
            """{"action":"DONE","at":1,"reminderId":5}"""
        )
        for (body in bodies) {
            val decoded = Codec.decode(Paths.ack(id), Envelope(Protocol.MAJOR_VERSION, id, ts, body))
            assertTrue("body $body -> $decoded", decoded is DecodeResult.Failure)
        }
        val atLimit = "r".repeat(Limits.MAX_KEY_LENGTH)
        val ok = Codec.decode(
            Paths.ack(id),
            Envelope(Protocol.MAJOR_VERSION, id, ts, """{"action":"DONE","at":1,"reminderId":"$atLimit"}""")
        )
        assertTrue(ok is DecodeResult.Ok)
    }

    @Test
    fun `TC-S-049 selection round trip keeps the chosen key and time`() {
        val selection = Selection("calm", at = ts - 1)
        assertEquals(selection, roundTrip(selection).message)
    }

    @Test
    fun `TC-S-050 alert round trip keeps metric, numeric value, threshold and time`() {
        val alert = Alert(Metric.HEART_RATE, value = 163.5, threshold = 150.0, at = ts - 2)
        assertEquals(alert, roundTrip(alert).message)
    }

    @Test
    fun `TC-S-051 hello round trip for both sides keeps protocol and app version`() {
        Side.entries.forEach {
            val hello = Hello(it, Protocol.VERSION, "0.3.1")
            assertEquals(hello, roundTrip(hello).message)
        }
    }

    @Test
    fun `TC-S-051 debug biometrics round trip`() {
        val samples = BiometricSamples(listOf(BiometricSample(ts, 71.0), BiometricSample(ts + 1000, 72.5)))
        assertEquals(samples, roundTrip(samples).message)
    }

    @Test
    fun `TC-S-052 a body with extra fields decodes and known fields are kept`() {
        val body = """{"key":"calm","at":42,"futureField":{"nested":[1,2,3]},"another":"x"}"""
        val decoded = Codec.decode(Paths.selection(id), Envelope(Protocol.MAJOR_VERSION, id, ts, body))
        assertEquals(DecodeResult.Ok(Item(id, ts, Selection("calm", 42))), decoded)
    }

    @Test
    fun `TC-S-053 invalid bodies are dropped with a typed failure and never throw`() {
        val path = Paths.selection(id)
        val bodies = listOf(
            """{"at":42}""",
            """{"key":"calm"}""",
            """{"key":5,"at":42}""",
            """{"key":"calm","at":"later"}""",
            """{"key":"calm","at":""",
            "",
            "[]",
            "null",
            "not json"
        )
        for (body in bodies) {
            val decoded = Codec.decode(path, Envelope(Protocol.MAJOR_VERSION, id, ts, body))
            assertTrue("body '$body' -> $decoded", decoded is DecodeResult.Failure)
        }
    }

    @Test
    fun `TC-S-053 an unknown path and an unknown enum value are failures`() {
        val envelope = Envelope(Protocol.MAJOR_VERSION, id, ts, """{"action":"EXPLODE","at":1}""")
        assertTrue(Codec.decode(Paths.ack(id), envelope) is DecodeResult.Failure)
        assertTrue(Codec.decode("/nope", envelope) is DecodeResult.Failure)
    }

    @Test
    fun `TC-S-053 models reject out-of-limit content on construction and on decode`() {
        val options = (1..Limits.MAX_OPTIONS + 1).map { """{"key":"k$it","label":"L"}""" }.joinToString(",")
        val body = """{"title":"p","options":[$options]}"""
        val decoded = Codec.decode(Paths.prompt(id), Envelope(Protocol.MAJOR_VERSION, id, ts, body))
        assertTrue("$decoded", decoded is DecodeResult.Failure)
    }

    @Test
    fun `TC-S-054 the largest legal schedule and prompt encode well under 100 KB`() {
        val big = Schedule(
            version = Long.MAX_VALUE,
            reminders = (1..Limits.MAX_REMINDERS).map {
                Reminder("r$it", "t".repeat(Limits.MAX_TEXT_LENGTH), 1439, listOf(1, 2, 3, 4, 5, 6, 7))
            },
            checkpoints = (1..Limits.MAX_CHECKPOINTS).map {
                Checkpoint("c$it", "l".repeat(Limits.MAX_TEXT_LENGTH), 1439)
            },
            snooze = SnoozeSettings(Limits.MAX_SNOOZE_MINUTES, Limits.MAX_SNOOZE_COUNT),
            alerts = AlertSettings(
                (1..Limits.MAX_THRESHOLDS).map { Threshold(Metric.HEART_RATE, Direction.ABOVE, 199.99) },
                Limits.MAX_COOLDOWN_SECONDS
            )
        )
        val prompt = Prompt(
            "p".repeat(Limits.MAX_TEXT_LENGTH),
            (1..Limits.MAX_OPTIONS).map {
                PromptOption("$it".padEnd(Limits.MAX_KEY_LENGTH, 'k'), "l".repeat(Limits.MAX_TEXT_LENGTH))
            },
            Long.MAX_VALUE
        )
        // Asserted bound: 40 KB, less than half of the 100 KB DataItem limit.
        for (message in listOf(big, prompt)) {
            val envelope = Codec.encode(id, ts, message)
            val size = envelope.body.toByteArray().size + envelope.id.length + 64
            assertTrue("size $size", size < 40 * 1024)
            assertEquals(
                Item(id, ts, message),
                (Codec.decode(Paths.pathFor(id, message), envelope) as DecodeResult.Ok).item
            )
        }
    }

    @Test
    fun `TC-S-055 the envelope has v, id, ts and a JSON string body`() {
        val envelope = Codec.encode(id, ts, schedule)
        assertEquals(Protocol.MAJOR_VERSION, envelope.version)
        assertEquals(id, envelope.id)
        assertEquals(ts, envelope.timestamp)
        assertTrue(envelope.body.startsWith("{") && envelope.body.endsWith("}"))
        assertEquals(
            listOf("v", "id", "ts", "body"),
            listOf(ItemKeys.VERSION, ItemKeys.ID, ItemKeys.TIMESTAMP, ItemKeys.BODY)
        )
    }

    @Test
    fun `TC-S-055 the protocol module imports no DataMap or Android class`() {
        val main = File("src/main")
        assertTrue("run from the module directory: ${File(".").absolutePath}", main.isDirectory)
        val offenders = main.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.readLines().any {
                    it.startsWith("import") &&
                        (it.contains("DataMap") || it.contains("android.") || it.contains("com.google.android"))
                }
            }
            .toList()
        assertFalse("offenders: $offenders", offenders.isNotEmpty())
    }
}

package com.shlomoa.myadhdpal.wear.prompt

import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.PromptOption
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Selection
import com.shlomoa.myadhdpal.protocol.fake.FakeLink
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptInboxTest {
    private val link = FakeLink()
    private var now = Instant.parse("2026-10-14T08:00:00Z")
    private val id = "55555555-5555-4555-8555-555555555555"
    private val otherId = "66666666-6666-4666-8666-666666666666"
    private val options = listOf(PromptOption("eat", "Eat now"), PromptOption("later", "Later"))

    private fun inbox() = PromptInbox(link.watch, { now }).also {
        link.watch.setListener(it)
        it.start()
    }

    private fun sendPrompt(promptId: String = id, prompt: Prompt = Prompt("Did you eat?", options), at: Instant = now) =
        link.phone.put(Paths.prompt(promptId), Codec.encode(promptId, at.toEpochMilli(), prompt))

    private fun selectionOnPhone(promptId: String = id): List<Selection> =
        link.phone.items().filter { it.path == Paths.selection(promptId) }.map {
            ((Codec.decode(it.path, it.envelope) as DecodeResult.Ok).item.message as Selection)
        }

    @Test
    fun `TC-W-040 a prompt from the phone is pending with its title and options`() {
        val inbox = inbox()
        link.connect(nearby = true)
        sendPrompt()
        val pending = inbox.pending().single()
        assertEquals(id, pending.id)
        assertEquals("Did you eat?", pending.prompt.title)
        assertEquals(options, pending.prompt.options)
    }

    @Test
    fun `TC-W-041 choosing an option sends the selection with the prompt id, the key and the watch time`() {
        val inbox = inbox()
        link.connect(nearby = true)
        sendPrompt()
        assertTrue(inbox.choose(id, "eat"))
        assertEquals(listOf(Selection("eat", now.toEpochMilli())), selectionOnPhone())
        assertTrue(inbox.pending().isEmpty())
    }

    @Test
    fun `TC-W-041 an unknown key is refused and a second answer sends nothing more`() {
        val inbox = inbox()
        link.connect(nearby = true)
        sendPrompt()
        assertFalse(inbox.choose(id, "nope"))
        assertTrue(selectionOnPhone().isEmpty())
        assertTrue(inbox.choose(id, "later"))
        assertFalse(inbox.choose(id, "eat"))
        assertEquals(listOf("later"), selectionOnPhone().map { it.key })
    }

    @Test
    fun `TC-W-041 an answer given while the phone is away is delivered on reconnect`() {
        val inbox = inbox()
        link.connect(nearby = true)
        sendPrompt()
        link.disconnect()
        assertTrue(inbox.choose(id, "eat"))
        assertTrue(selectionOnPhone().isEmpty())
        link.connect(nearby = false)
        assertEquals(listOf("eat"), selectionOnPhone().map { it.key })
    }

    @Test
    fun `TC-W-042 a prompt that is already expired when it arrives is not pending`() {
        val inbox = inbox()
        link.connect(nearby = true)
        sendPrompt(prompt = Prompt("Old", options, expiresAt = now.toEpochMilli()))
        assertTrue(inbox.pending().isEmpty())
        assertFalse(inbox.choose(id, "eat"))
        assertTrue(selectionOnPhone().isEmpty())
    }

    @Test
    fun `TC-W-043 a pending prompt is dropped once the watch clock reaches its expiry`() {
        val inbox = inbox()
        link.connect(nearby = true)
        sendPrompt(prompt = Prompt("Soon", options, expiresAt = now.plusSeconds(30).toEpochMilli()))
        assertEquals(1, inbox.pending().size)
        now = now.plusSeconds(30)
        assertTrue(inbox.pending().isEmpty())
        assertFalse(inbox.choose(id, "eat"))
    }

    @Test
    fun `TC-W-044 an invalid or unsupported-major prompt is dropped`() {
        val inbox = inbox()
        link.connect(nearby = true)
        val good = Codec.encode(id, now.toEpochMilli(), Prompt("Fine", options))
        link.phone.put(Paths.prompt(id), good.copy(body = "{ nope"))
        link.phone.put(Paths.prompt(otherId), good.copy(id = otherId, version = Protocol.MAJOR_VERSION + 1))
        assertTrue(inbox.pending().isEmpty())
    }

    @Test
    fun `TC-W-040 the oldest prompt comes first and a prompt the phone deletes disappears`() {
        val inbox = inbox()
        link.connect(nearby = true)
        sendPrompt(otherId, Prompt("Second", options), at = now.plusSeconds(5))
        sendPrompt(id, Prompt("First", options), at = now)
        assertEquals(listOf(id, otherId), inbox.pending().map { it.id })
        link.phone.delete(Paths.prompt(id))
        assertEquals(listOf(otherId), inbox.pending().map { it.id })
    }

    @Test
    fun `TC-W-041 the watch never deletes the phone's prompt and does not list an answered one after a restart`() {
        val inbox = inbox()
        link.connect(nearby = true)
        sendPrompt()
        inbox.choose(id, "eat")
        assertTrue(link.watch.items().any { it.path == Paths.prompt(id) })

        val restarted = PromptInbox(link.watch, { now }).apply { start() }
        assertTrue(restarted.pending().isEmpty())
    }

    @Test
    fun `TC-W-040 the change callback fires when a prompt arrives and when it is answered`() {
        val inbox = inbox()
        var changes = 0
        inbox.setOnChange { changes++ }
        link.connect(nearby = true)
        sendPrompt()
        inbox.choose(id, "eat")
        assertEquals(2, changes)
    }
}

package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryPublishedPrompts
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Selection
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue 198: the phone removes a prompt it wrote when the answer is stored and when the prompt has expired. */
class PromptCleanerTest {
    private val answered = "123e4567-e89b-42d3-a456-426614174001"
    private val other = "123e4567-e89b-42d3-a456-426614174002"
    private val reminderAck = "123e4567-e89b-42d3-a456-426614174003"
    private var now = Instant.parse("2026-10-17T10:00:00Z")
    private val source = FakeWatchLinkDataSource()
    private val prompts = InMemoryPublishedPrompts()
    private val cleaner = PromptCleaner(source, prompts) { now }

    private fun ack(id: String) = WatchEvent.AckReceived(id, 1, Acknowledgement(AckAction.DONE, 1))

    private fun selection(id: String) = WatchEvent.SelectionReceived(id, 1, Selection("1", 1))

    @Test
    fun `TC-S-190 a stored ack or selection for a published prompt removes that prompt once`() = runTest {
        prompts.add(answered, null)
        prompts.add(other, null)
        cleaner.answered(ack(answered))
        cleaner.answered(ack(answered))
        cleaner.answered(selection(other))
        assertEquals(listOf(answered, other), source.removedPrompts)
        assertTrue(prompts.all().isEmpty())
    }

    @Test
    fun `TC-S-190 an ack that answers no published prompt removes nothing`() = runTest {
        prompts.add(answered, null)
        cleaner.answered(ack(reminderAck))
        cleaner.answered(WatchEvent.AlertReceived(other, 1, Alert(Metric.HEART_RATE, 130.0, 110.0, 1)))
        assertTrue(source.removedPrompts.isEmpty())
        assertEquals(listOf(answered), prompts.all().map { it.id })
    }

    @Test
    fun `TC-S-191 the sweep removes expired prompts only and keeps open and unanswered ones`() = runTest {
        prompts.add("expired", now.minusSeconds(1))
        prompts.add("due", now)
        prompts.add("open", now.plusSeconds(60))
        prompts.add("never", null)
        cleaner.sweepExpired()
        assertEquals(setOf("expired", "due"), source.removedPrompts.toSet())
        assertEquals(setOf("open", "never"), prompts.all().map { it.id }.toSet())
    }

    @Test
    fun `TC-S-191 a prompt is removed once its expiry passes and a second sweep removes nothing more`() = runTest {
        prompts.add("p", now.plusSeconds(60))
        cleaner.sweepExpired()
        assertTrue(source.removedPrompts.isEmpty())
        now = now.plusSeconds(61)
        cleaner.sweepExpired()
        cleaner.sweepExpired()
        assertEquals(listOf("p"), source.removedPrompts)
    }
}

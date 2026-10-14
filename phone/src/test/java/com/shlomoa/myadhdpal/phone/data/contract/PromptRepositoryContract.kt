package com.shlomoa.myadhdpal.phone.data.contract

import com.shlomoa.myadhdpal.phone.data.PromptError
import com.shlomoa.myadhdpal.phone.data.PromptPublishResult
import com.shlomoa.myadhdpal.phone.data.PromptRepository
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.protocol.Limits
import com.shlomoa.myadhdpal.protocol.Paths
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every [PromptRepository] must pass these. The clock is fixed at [now]. */
abstract class PromptRepositoryContract {
    abstract fun create(source: WatchLinkDataSource, now: Instant): PromptRepository

    private val now = Instant.parse("2026-10-14T12:00:00Z")

    @Test
    fun `TC-S-092 prompt repository publishes a prompt with options and an optional expiry`() = runTest {
        val source = FakeWatchLinkDataSource()
        val repo = create(source, now)
        val withExpiry = repo.publish("Mood", listOf("Calm", "Tense"), now.plusSeconds(600)) as PromptPublishResult.Published
        val without = repo.publish("Water", listOf("Done", "Later"), null) as PromptPublishResult.Published
        assertTrue(Paths.isValidId(withExpiry.id))
        assertEquals(2, source.publishedPrompts.size)
        val (id, prompt) = source.publishedPrompts.first()
        assertEquals(withExpiry.id, id)
        assertEquals(listOf("Calm", "Tense"), prompt.options.map { it.label })
        assertEquals(now.plusSeconds(600).toEpochMilli(), prompt.expiresAt)
        assertNull(source.publishedPrompts[1].second.expiresAt)
        assertTrue("two prompts get two ids", withExpiry.id != without.id)
    }

    @Test
    fun `TC-S-092 prompt repository rejects a blank title, too few or too many options and a past expiry`() = runTest {
        val repo = create(FakeWatchLinkDataSource(), now)
        assertEquals(PromptPublishResult.Rejected(setOf(PromptError.BLANK_TITLE)), repo.publish(" ", listOf("A", "B"), null))
        assertEquals(PromptPublishResult.Rejected(setOf(PromptError.TOO_FEW_OPTIONS)), repo.publish("T", listOf("A"), null))
        val many = (1..Limits.MAX_OPTIONS + 1).map { "Option $it" }
        assertEquals(PromptPublishResult.Rejected(setOf(PromptError.TOO_MANY_OPTIONS)), repo.publish("T", many, null))
        assertEquals(PromptPublishResult.Rejected(setOf(PromptError.EXPIRY_IN_PAST)), repo.publish("T", listOf("A", "B"), now))
        val long = "x".repeat(Limits.MAX_TEXT_LENGTH + 1)
        assertEquals(PromptPublishResult.Rejected(setOf(PromptError.TITLE_TOO_LONG)), repo.publish(long, listOf("A", "B"), null))
    }

    @Test
    fun `TC-S-092 prompt repository reports a failed publish`() = runTest {
        val source = FakeWatchLinkDataSource()
        source.failNextPublish("no link")
        val repo = create(source, now)
        assertEquals(PromptPublishResult.Failed("no link"), repo.publish("T", listOf("A", "B"), null))
        assertTrue(source.publishedPrompts.isEmpty())
    }
}

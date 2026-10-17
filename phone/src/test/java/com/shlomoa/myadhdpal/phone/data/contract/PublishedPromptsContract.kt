package com.shlomoa.myadhdpal.phone.data.contract

import com.shlomoa.myadhdpal.phone.data.PublishedPrompt
import com.shlomoa.myadhdpal.phone.data.PublishedPrompts
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What every [PublishedPrompts] must do (issue 198): remember the prompts the phone wrote until it removes them. */
abstract class PublishedPromptsContract {
    abstract fun create(): PublishedPrompts

    private val expiry = Instant.parse("2026-10-17T12:00:00Z")

    @Test
    fun `TC-S-195 a new store is empty and added prompts are listed with their expiry`() = runTest {
        val prompts = create()
        assertTrue(prompts.all().isEmpty())
        prompts.add("p1", expiry)
        prompts.add("p2", null)
        assertEquals(setOf(PublishedPrompt("p1", expiry), PublishedPrompt("p2", null)), prompts.all().toSet())
    }

    @Test
    fun `TC-S-195 remove forgets one prompt and removing an unknown id does nothing`() = runTest {
        val prompts = create()
        prompts.add("p1", expiry)
        prompts.add("p2", null)
        prompts.remove("p1")
        prompts.remove("unknown")
        assertEquals(listOf(PublishedPrompt("p2", null)), prompts.all())
    }

    @Test
    fun `TC-S-195 adding the same id again replaces its expiry`() = runTest {
        val prompts = create()
        prompts.add("p1", expiry)
        prompts.add("p1", null)
        assertEquals(listOf(PublishedPrompt("p1", null)), prompts.all())
    }
}

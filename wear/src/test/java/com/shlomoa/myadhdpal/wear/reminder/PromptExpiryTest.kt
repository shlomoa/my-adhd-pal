package com.shlomoa.myadhdpal.wear.reminder

import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.PromptOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `expiresAt` is a phone-clock time in epoch milliseconds that the watch compares with its own clock. */
class PromptExpiryTest {
    private val now = 1_760_000_000_000L
    private val options = listOf(PromptOption("a", "Yes"), PromptOption("b", "No"))

    private fun prompt(expiresAt: Long?) = Prompt("Did you eat?", options, expiresAt)

    @Test
    fun `TC-W-042 a prompt whose expiry is at or before the watch clock is expired and is not shown`() {
        assertTrue(prompt(now - 1).isExpiredAt(now))
        assertTrue(prompt(now).isExpiredAt(now))
    }

    @Test
    fun `TC-W-042 a prompt with a later expiry or none is still answerable`() {
        assertFalse(prompt(now + 1).isExpiredAt(now))
        assertFalse(prompt(null).isExpiredAt(now))
    }

    @Test
    fun `TC-W-043 a shown prompt is hidden when the watch clock reaches its expiry`() {
        assertEquals(30_000L, prompt(now + 30_000).millisUntilExpiry(now))
        assertEquals(0L, prompt(now).millisUntilExpiry(now))
        assertEquals(0L, prompt(now - 5).millisUntilExpiry(now))
        assertNull(prompt(null).millisUntilExpiry(now))
    }
}

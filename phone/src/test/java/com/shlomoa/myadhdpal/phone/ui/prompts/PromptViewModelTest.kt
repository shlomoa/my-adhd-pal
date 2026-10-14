package com.shlomoa.myadhdpal.phone.ui.prompts

import com.shlomoa.myadhdpal.phone.data.PromptError
import com.shlomoa.myadhdpal.phone.data.PromptPublishResult
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryPromptRepository
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import com.shlomoa.myadhdpal.phone.ui.NOW
import com.shlomoa.myadhdpal.protocol.Limits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Step 4.4: offer the watch a prompt with options. The phone removes its own prompt item (writer owns cleanup). */
class PromptViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val source = FakeWatchLinkDataSource()
    private val vm = PromptViewModel(InMemoryPromptRepository(source, clock = { NOW }))

    @Test
    fun `TC-A-039 publish a prompt with a title and options`() {
        vm.publish("How do you feel?", listOf("Calm", "Tense"), expiresAt = null)
        val (id, prompt) = source.publishedPrompts.single()
        assertEquals("How do you feel?", prompt.title)
        assertEquals(listOf("Calm", "Tense"), prompt.options.map { it.label })
        assertEquals(PromptPublishResult.Published(id), vm.uiState.value.result)
    }

    @Test
    fun `TC-A-040 a prompt needs a title and at least two options`() {
        vm.publish(" ", listOf("Only"), null)
        assertEquals(
            PromptPublishResult.Rejected(setOf(PromptError.BLANK_TITLE, PromptError.TOO_FEW_OPTIONS)),
            vm.uiState.value.result
        )
        assertTrue(source.publishedPrompts.isEmpty())
    }

    @Test
    fun `TC-A-041 too many options are rejected`() {
        vm.publish("Pick", List(Limits.MAX_OPTIONS + 1) { "o$it" }, null)
        assertEquals(PromptPublishResult.Rejected(setOf(PromptError.TOO_MANY_OPTIONS)), vm.uiState.value.result)
    }

    @Test
    fun `TC-A-042 the optional expiry is carried`() {
        vm.publish("Mood", listOf("a", "b"), NOW.plusSeconds(600))
        vm.publish("Water", listOf("a", "b"), null)
        assertEquals(NOW.plusSeconds(600).toEpochMilli(), source.publishedPrompts[0].second.expiresAt)
        assertNull(source.publishedPrompts[1].second.expiresAt)
    }

    @Test
    fun `TC-A-043 each prompt gets its own id`() {
        vm.publish("One", listOf("a", "b"), null)
        vm.publish("Two", listOf("a", "b"), null)
        assertNotEquals(source.publishedPrompts[0].first, source.publishedPrompts[1].first)
    }
}

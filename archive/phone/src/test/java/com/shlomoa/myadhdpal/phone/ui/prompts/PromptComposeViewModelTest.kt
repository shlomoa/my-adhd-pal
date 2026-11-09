package com.shlomoa.myadhdpal.phone.ui.prompts

import com.shlomoa.myadhdpal.phone.data.PromptError
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryPromptRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryPublishedPrompts
import com.shlomoa.myadhdpal.phone.ui.FIXED_CLOCK
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import com.shlomoa.myadhdpal.phone.ui.NOW
import com.shlomoa.myadhdpal.phone.ui.SequentialIds
import com.shlomoa.myadhdpal.protocol.Limits
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Step 4.4: asking the watch a question with a few answers to choose from. */
class PromptComposeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val source = FakeWatchLinkDataSource { NOW }
    private val ids = SequentialIds("prompt")
    private val repo = InMemoryPromptRepository(source, InMemoryPublishedPrompts(), { NOW }, ids::next)
    private val vm by lazy { PromptComposeViewModel(repo, FIXED_CLOCK) }

    private fun fill(title: String, vararg options: String) {
        vm.setTitle(title)
        options.forEachIndexed { index, label ->
            if (index >= vm.uiState.value.options.size) vm.addOption()
            vm.setOption(index, label)
        }
    }

    @Test
    fun `TC-A-128 the form starts with an empty title, two empty answers and no expiry`() {
        assertEquals(PromptComposeUiState(), vm.uiState.value)
        assertEquals(listOf("", ""), vm.uiState.value.options)
        assertNull(vm.uiState.value.expiryMinutes)
    }

    @Test
    fun `TC-A-039 sending publishes the title and the answers in order, without empty answers`() = runTest {
        fill("Did you take the pills?", "Yes", "", "Later")
        vm.send()
        val (id, prompt) = source.publishedPrompts.single()
        assertEquals("prompt-1", id)
        assertEquals("Did you take the pills?", prompt.title)
        assertEquals(listOf("Yes", "Later"), prompt.options.map { it.label })
        assertEquals(listOf("1", "2"), prompt.options.map { it.key })
        assertNull(prompt.expiresAt)
        assertEquals(PromptStatus.Sent, vm.uiState.value.status)
    }

    @Test
    fun `TC-A-040 a blank title or fewer than two answers is rejected and nothing is sent`() = runTest {
        fill("", "Yes", "No")
        vm.send()
        assertEquals(setOf(PromptError.BLANK_TITLE), vm.uiState.value.errors)
        fill("Question", "Yes", "")
        vm.send()
        assertEquals(setOf(PromptError.TOO_FEW_OPTIONS), vm.uiState.value.errors)
        assertTrue(source.publishedPrompts.isEmpty())
        assertEquals("Question", vm.uiState.value.title)
    }

    @Test
    fun `TC-A-041 answers can be added up to the limit and removed down to two`() {
        repeat(Limits.MAX_OPTIONS) { vm.addOption() }
        assertEquals(Limits.MAX_OPTIONS, vm.uiState.value.options.size)
        vm.setOption(0, "first")
        vm.removeOption(0)
        assertEquals(Limits.MAX_OPTIONS - 1, vm.uiState.value.options.size)
        repeat(Limits.MAX_OPTIONS) { vm.removeOption(0) }
        assertEquals(2, vm.uiState.value.options.size)
    }

    @Test
    fun `TC-A-042 a chosen expiry is carried as a time from now, and none when it is not chosen`() = runTest {
        fill("Water?", "Yes", "No")
        vm.setExpiry(15)
        vm.send()
        assertEquals(NOW.plus(Duration.ofMinutes(15)).toEpochMilli(), source.publishedPrompts.single().second.expiresAt)
    }

    @Test
    fun `TC-A-133 a failed send shows the reason and keeps the form, a sent one empties it`() = runTest {
        fill("Water?", "Yes", "No")
        source.failNextPublish("watch not reachable")
        vm.send()
        assertEquals(PromptStatus.Failed("watch not reachable"), vm.uiState.value.status)
        assertEquals("Water?", vm.uiState.value.title)
        assertTrue(source.publishedPrompts.isEmpty())
        vm.send()
        assertEquals(PromptStatus.Sent, vm.uiState.value.status)
        assertEquals(PromptComposeUiState(status = PromptStatus.Sent), vm.uiState.value)
    }

    @Test
    fun `TC-A-043 two prompts get two ids`() = runTest {
        fill("One?", "Yes", "No")
        vm.send()
        fill("Two?", "Yes", "No")
        vm.send()
        assertEquals(listOf("prompt-1", "prompt-2"), source.publishedPrompts.map { it.first })
    }
}

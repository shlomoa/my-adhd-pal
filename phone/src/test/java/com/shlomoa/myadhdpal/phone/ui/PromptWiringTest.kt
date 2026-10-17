package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.shlomoa.myadhdpal.phone.MainActivity
import com.shlomoa.myadhdpal.phone.link.DataLayerClient
import com.shlomoa.myadhdpal.phone.link.FakeDataLayerClient
import com.shlomoa.myadhdpal.protocol.Paths
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Step 4.4: the Ask tab publishes a prompt on the real graph. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class PromptWiringTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject
    lateinit var client: DataLayerClient

    @Before
    fun inject() = hilt.inject()

    private fun waitForText(text: String) = compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `TC-A-135 the Ask tab sends a question with its answers to the watch`() {
        compose.onNodeWithText("Ask").performClick()
        compose.onNode(hasText("Your question")).performTextInput("Did you take the pills?")
        compose.onNode(hasText("Answer 1")).performTextInput("Yes")
        compose.onNode(hasText("Answer 2")).performTextInput("Not yet")
        compose.onNodeWithText("Send to the watch").performScrollTo().performClick()

        waitForText("Question sent to the watch")
        val puts = (client as FakeDataLayerClient).puts.filter { it.path.startsWith(Paths.PROMPT_PREFIX) }
        assertEquals(1, puts.size)
        assertTrue(puts.single().envelope.body.contains("Did you take the pills?"))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

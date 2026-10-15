package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.shlomoa.myadhdpal.phone.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Step 4.1: adding, listing and deleting a task through the app, on the real graph (Room included). */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TasksWiringTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    private fun waitForText(text: String) = compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForNoText(text: String) = compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()
    }

    @Test
    fun `TC-A-089 a task can be added, listed and deleted in the app`() {
        compose.onNodeWithText("Tasks").performClick()
        waitForText("No tasks yet")

        compose.onNodeWithText("Add a task").performClick()
        compose.onNode(hasText("What do you want to remember?")).performTextInput("Take the pills")
        compose.onNodeWithText("Save").performClick()
        waitForText("Take the pills")
        waitForNoText("What do you want to remember?")

        compose.onNodeWithText("Delete").performClick()
        waitForText("No tasks yet")
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 10_000L
    }
}

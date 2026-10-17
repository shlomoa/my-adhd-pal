package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** Step 4.1: a task gets a reminder through the app, on the real graph (Room included). */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RemindersWiringTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    private fun waitForText(text: String) = compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForNoText(text: String) = compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()
    }

    /** Buttons can sit below the fold in a scrolling form; a user scrolls to them, so the test does too. */
    private fun press(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    @Test
    fun `TC-A-119 save a task, add a reminder, see it listed, open it and delete it`() {
        compose.onNodeWithText("Tasks").performClick()
        waitForText("No tasks yet")
        compose.onNodeWithText("Add a task").performClick()
        compose.onNode(hasText("What do you want to remember?")).performTextInput("Take the pills")
        press("Save")

        // A new task opens again with its reminders, so one can be added at once.
        waitForText("No reminders yet")
        press("Add a reminder")
        waitForText("Days")
        press("Save")
        waitForText("09:00")
        waitForText("Every day")
        waitForNoText("No reminders yet")

        compose.onNodeWithText("09:00", substring = true).performClick()
        waitForText("Days")
        press("Delete")
        waitForText("No reminders yet")
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

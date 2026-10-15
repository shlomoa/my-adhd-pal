package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** The real Hilt graph; the Play services client is faked for every Hilt test by [FakeLinkTestModule]. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MainActivityWiringTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Test
    fun `TC-A-087 MainActivity shows the link state and the history from the real graph`() {
        compose.onNodeWithText("Never synced").assertExists()
        compose.onNodeWithText("History").performClick()
        // Room answers on its own executor, which Compose's idling does not track: until it does, the screen
        // correctly shows "Loading...". Wait for the content instead of asserting at once (slow CI runners).
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(
                "Nothing from your watch yet",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Nothing from your watch yet", substring = true).assertExists()
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 10_000L
    }
}

package com.shlomoa.myadhdpal.phone.ui

import android.os.Looper
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.shlomoa.myadhdpal.phone.MainActivity
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Step 4.3 in the app: the Settings tab on the real graph (Room, DataStore), only the Play services client faked. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SettingsWiringTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun inject() = hilt.inject()

    private fun waitForText(text: String) = compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /**
     * DataStore runs a write's transform in the caller's context, and the ViewModel's is the main thread, so the main
     * looper (paused in Robolectric) must run before the stored value can change. On a device it simply runs.
     */
    private fun storedOffset(): Int {
        shadowOf(Looper.getMainLooper()).idle()
        return runBlocking { settings.settings.first().offsetBpm }
    }

    /** The button is below the fields in a scrolling column; a user scrolls to it, so the test does too. */
    private fun save() {
        compose.onNodeWithText("Save settings").performScrollTo().performClick()
    }

    private fun setOffset(text: String) {
        val field = compose.onNode(hasText("Alert offset (bpm)"))
        field.performTextClearance()
        field.performTextInput(text)
    }

    @Test
    fun `TC-A-115 the Settings tab saves a changed offset and shows an error for an out of range one`() {
        compose.onNodeWithText("Settings").performClick()
        waitForText("Alert offset (bpm)")
        waitForText("The watch has not reported yet")

        setOffset("40")
        save()
        compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { storedOffset() == 40 }

        setOffset("99")
        save()
        waitForText("Choose a number from 20 to 50")
        assertEquals(40, storedOffset())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

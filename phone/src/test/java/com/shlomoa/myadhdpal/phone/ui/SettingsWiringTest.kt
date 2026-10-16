package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    private fun storedOffset() = runBlocking { settings.settings.first().offsetBpm }

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
        compose.onNodeWithText("Save settings").performClick()
        compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { storedOffset() == 40 }

        setOffset("99")
        compose.onNodeWithText("Save settings").performClick()
        waitForText("Choose a number from 20 to 50")
        assertEquals(40, storedOffset())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

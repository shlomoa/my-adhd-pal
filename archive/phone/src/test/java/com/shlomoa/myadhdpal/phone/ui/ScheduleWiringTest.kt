package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shlomoa.myadhdpal.phone.MainActivity
import com.shlomoa.myadhdpal.phone.link.DataLayerClient
import com.shlomoa.myadhdpal.phone.link.FakeDataLayerClient
import com.shlomoa.myadhdpal.protocol.Paths
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Step 4.2: the Tasks tab sends the schedule to the watch on the real graph (Room and DataStore included). */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ScheduleWiringTest {
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
    fun `TC-A-127 the Tasks tab sends the schedule once and then says the watch is up to date`() {
        compose.onNodeWithText("Tasks").performClick()
        waitForText("Not sent to the watch yet")

        compose.onNodeWithText("Send schedule to the watch").performClick()
        waitForText("Sent to the watch (version 1)")
        val puts = (client as FakeDataLayerClient).puts.filter { it.path == Paths.SCHEDULE }
        assertEquals(1, puts.size)

        compose.onNodeWithText("Send schedule to the watch").performClick()
        waitForText("The watch is already up to date")
        assertEquals(1, (client as FakeDataLayerClient).puts.count { it.path == Paths.SCHEDULE })
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

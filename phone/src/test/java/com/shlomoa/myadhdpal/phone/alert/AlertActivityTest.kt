package com.shlomoa.myadhdpal.phone.alert

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.link.DataLayerClient
import com.shlomoa.myadhdpal.phone.link.FakeDataLayerClient
import com.shlomoa.myadhdpal.protocol.ItemPath
import com.shlomoa.myadhdpal.protocol.Metric
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

/** The alert screen on the real Hilt graph; only the Play services client is faked (by `FakeLinkTestModule`). */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AlertActivityTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject
    lateinit var client: DataLayerClient

    @Before
    fun inject() = hilt.inject()

    @Test
    fun `TC-A-108 I'm OK on the alert screen writes the episode end for that episode and closes the screen`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val episode = "6f1c2d3e-4b5a-4c7d-8e9f-0a1b2c3d4e5f"
        val alert = HistoryEntry.AlertRaised("a-1", 1_000, Metric.HEART_RATE, 138.0, 110.0, level = 2, episodeId = episode)
        val intent = AlertActivity.intent(context, alert, AlertPlanner.plan(2, AlertPrefs(), true))
        ActivityScenario.launch<AlertActivity>(intent).use { scenario ->
            compose.onNodeWithText("I'm OK").performClick()
            compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                (client as FakeDataLayerClient).puts.any { Paths.parse(it.path) is ItemPath.EpisodeEnd }
            }
            val put = (client as FakeDataLayerClient).puts.single { it.path.startsWith(Paths.EPISODE_END_PREFIX) }
            assertTrue(put.urgent)
            assertTrue(put.envelope.body.contains(episode))
            compose.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { scenario.state == Lifecycle.State.DESTROYED }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

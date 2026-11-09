package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shlomoa.myadhdpal.phone.ui.history.HistoryUiState
import com.shlomoa.myadhdpal.phone.ui.link.LastSync
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateUiState
import com.shlomoa.myadhdpal.phone.ui.permissions.AlertPermissions
import com.shlomoa.myadhdpal.protocol.LinkState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Step 2.6 and 4.5 wiring: the home screen holds the link-state screen and the history screen as two tabs. */
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TC-A-086 the link state shows first and the history tab switches to the history`() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    link = LinkStateUiState(LinkState.NEARBY, LastSync.Never),
                    history = HistoryUiState(null, ListState.Empty),
                    onFilter = {},
                    onRetry = {},
                    tasks = {},
                    settings = {},
                    ask = {},
                    alertPermissions = AlertPermissions(notifications = true, fullScreen = true),
                    onAllowNotifications = {},
                    onAllowFullScreen = {}
                )
            }
        }
        compose.onNodeWithText("Watch nearby").assertExists()
        compose.onNodeWithText("Nothing from your watch yet", substring = true).assertDoesNotExist()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Nothing from your watch yet", substring = true).assertExists()
    }

    @Test
    fun `TC-A-148 the home screen opens on the requested tab`() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    link = LinkStateUiState(LinkState.NEARBY, LastSync.Never),
                    history = HistoryUiState(null, ListState.Empty),
                    onFilter = {},
                    onRetry = {},
                    tasks = {},
                    settings = {},
                    ask = {},
                    alertPermissions = AlertPermissions(notifications = true, fullScreen = true),
                    onAllowNotifications = {},
                    onAllowFullScreen = {},
                    initialTab = HomeTab.HISTORY
                )
            }
        }
        compose.onNodeWithText("Nothing from your watch yet", substring = true).assertExists()
        compose.onNodeWithText("Watch nearby").assertDoesNotExist()
    }

    @Test
    fun `TC-A-149 a tab is named by lower case text, anything else opens the watch tab`() {
        assertEquals(HomeTab.HISTORY, HomeTab.named("history"))
        assertEquals(HomeTab.SETTINGS, HomeTab.named("Settings"))
        assertEquals(HomeTab.ASK, HomeTab.named("ask"))
        assertEquals(HomeTab.TASKS, HomeTab.named("tasks"))
        assertEquals(HomeTab.WATCH, HomeTab.named(null))
        assertEquals(HomeTab.WATCH, HomeTab.named("nonsense"))
    }
}

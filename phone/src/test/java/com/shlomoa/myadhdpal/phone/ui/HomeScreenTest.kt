package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shlomoa.myadhdpal.phone.ui.history.HistoryUiState
import com.shlomoa.myadhdpal.phone.ui.link.LastSync
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateUiState
import com.shlomoa.myadhdpal.protocol.LinkState
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
                    tasks = {}
                )
            }
        }
        compose.onNodeWithText("Watch nearby").assertExists()
        compose.onNodeWithText("Nothing from your watch yet", substring = true).assertDoesNotExist()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Nothing from your watch yet", substring = true).assertExists()
    }
}

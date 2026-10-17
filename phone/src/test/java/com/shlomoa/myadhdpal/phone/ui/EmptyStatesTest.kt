package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.ui.reminders.RemindersSection
import com.shlomoa.myadhdpal.phone.ui.schedule.ScheduleSyncBar
import com.shlomoa.myadhdpal.phone.ui.schedule.ScheduleSyncUiState
import com.shlomoa.myadhdpal.phone.ui.schedule.SyncStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Step 4.6: what the user sees when there is nothing yet, or when it went wrong. */
@RunWith(RobolectricTestRunner::class)
class EmptyStatesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TC-A-055 with nothing scheduled the screens say so and offer the next step`() {
        compose.setContent {
            RemindersSection(ListState.Empty, onAdd = {}, onOpen = {})
        }
        compose.onNodeWithText("No reminders yet", substring = true).assertExists()
        compose.onNodeWithText("Add a reminder").assertExists()
    }

    @Test
    fun `TC-A-055 a schedule that was never sent says so and offers to send it`() {
        var sent = false
        compose.setContent {
            ScheduleSyncBar(ScheduleSyncUiState(null, SyncStatus.Idle), onSend = { sent = true })
        }
        compose.onNodeWithText("Not sent to the watch yet").assertExists()
        compose.onNodeWithText("Send schedule to the watch").performClick()
        assertTrue(sent)
    }

    @Test
    fun `TC-A-056 a failed reminder list shows the reason`() {
        compose.setContent {
            RemindersSection(ListState.Failed("disk full"), onAdd = {}, onOpen = {})
        }
        compose.onNodeWithText("disk full", substring = true).assertExists()
    }

    @Test
    fun `TC-A-057 a reminder list that is still loading says so`() {
        compose.setContent {
            RemindersSection(ListState.Loading, onAdd = {}, onOpen = {})
        }
        compose.onNodeWithText("Loading", substring = true).assertExists()
    }

    @Test
    fun `a reminder row opens that reminder`() {
        var opened: TaskReminder? = null
        val reminder = TaskReminder("r-1", "t-1", 9 * 60, listOf(1, 2, 3, 4, 5, 6, 7))
        compose.setContent {
            RemindersSection(ListState.Content(listOf(reminder)), onAdd = {}, onOpen = { opened = it })
        }
        compose.onNodeWithText("09:00", substring = true).performClick()
        assertTrue(opened == reminder)
    }
}

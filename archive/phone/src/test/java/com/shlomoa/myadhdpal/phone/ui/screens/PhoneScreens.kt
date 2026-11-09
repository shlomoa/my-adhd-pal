package com.shlomoa.myadhdpal.phone.ui.screens

import androidx.compose.runtime.Composable
import com.shlomoa.myadhdpal.phone.alert.AlertPrefs
import com.shlomoa.myadhdpal.phone.alert.AlertScreen
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.SettingsDefaults
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.ui.HomeScreen
import com.shlomoa.myadhdpal.phone.ui.ListState
import com.shlomoa.myadhdpal.phone.ui.history.HistoryUiState
import com.shlomoa.myadhdpal.phone.ui.link.LastSync
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateUiState
import com.shlomoa.myadhdpal.phone.ui.permissions.AlertPermissions
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptComposeActions
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptComposeScreen
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptComposeUiState
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptStatus
import com.shlomoa.myadhdpal.phone.ui.settings.SettingsScreen
import com.shlomoa.myadhdpal.phone.ui.settings.SettingsUiState
import com.shlomoa.myadhdpal.phone.ui.tasks.TaskListScreen
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.HeartRatePermission
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.WatchStatus
import java.time.Duration

/** One screen of the phone walkthrough: [content] is the real composable with fixed example data. */
class PhoneScreen(val id: String, val title: String, val openTab: String?, val content: @Composable () -> Unit)

/** The screens rendered by [PhoneScreensTest]. */
object PhoneScreens {
    private val granted = AlertPermissions(notifications = true, fullScreen = true)

    private fun home(
        link: LinkStateUiState = LinkStateUiState(LinkState.NEARBY, LastSync.Ago(Duration.ofMinutes(2))),
        history: HistoryUiState = HistoryUiState(null, ListState.Empty),
        permissions: AlertPermissions = granted,
        tasks: @Composable () -> Unit = {},
        settings: @Composable () -> Unit = {},
        ask: @Composable () -> Unit = {}
    ): @Composable () -> Unit = {
        HomeScreen(
            link = link,
            history = history,
            onFilter = {},
            onRetry = {},
            tasks = tasks,
            settings = settings,
            ask = ask,
            alertPermissions = permissions,
            onAllowNotifications = {},
            onAllowFullScreen = {}
        )
    }

    private fun linkScreen(id: String, title: String, state: LinkState, sync: LastSync) =
        PhoneScreen(id, title, null, home(link = LinkStateUiState(state, sync)))

    private val entries = listOf(
        HistoryEntry.AlertRaised("l-1", 1_760_000_300_000, Metric.HEART_RATE, 138.0, 110.0, 1, "e-1"),
        HistoryEntry.Selected("s-1", 1_760_000_200_000, "2", "How do you feel?"),
        HistoryEntry.Acknowledged("a-1", 1_760_000_100_000, AckAction.DONE, "demo", 1_760_000_050_000)
    )

    private val settingsState = SettingsUiState(
        settings = SettingsDefaults.settings,
        errors = emptySet(),
        watchStatus = WatchStatus(
            baselineBpm = 68,
            thresholdBpm = 98,
            heartRatePermission = HeartRatePermission.GRANTED
        ),
        alert = AlertPrefs()
    )

    private val ask = PromptComposeUiState(
        title = "How do you feel?",
        options = listOf("Fine", "A bit stressed", "Need a break"),
        expiryMinutes = 30,
        status = PromptStatus.Sent
    )

    private val askActions = PromptComposeActions({}, { _, _ -> }, {}, {}, {}, {})

    val all: List<PhoneScreen> = listOf(
        linkScreen("01-watch-nearby", "Watch: nearby", LinkState.NEARBY, LastSync.Ago(Duration.ofMinutes(2))),
        linkScreen("02-watch-relayed", "Watch: relayed", LinkState.RELAYED, LastSync.Ago(Duration.ofMinutes(5))),
        linkScreen("03-watch-away", "Watch: away", LinkState.AWAY, LastSync.Never),
        linkScreen("04-watch-no-app", "Watch: no watch app", LinkState.NO_WATCH_APP, LastSync.Never),
        linkScreen("05-watch-incompatible", "Watch: incompatible", LinkState.INCOMPATIBLE, LastSync.Never),
        PhoneScreen(
            "06-permissions",
            "Alert permissions missing",
            null,
            home(permissions = AlertPermissions(notifications = false, fullScreen = false))
        ),
        PhoneScreen(
            "07-history",
            "History: acknowledgement, answer, alert",
            "History",
            home(history = HistoryUiState(null, ListState.Content(entries)))
        ),
        PhoneScreen("08-history-empty", "History: empty", "History", home()),
        PhoneScreen(
            "09-history-failed",
            "History: failed",
            "History",
            home(history = HistoryUiState(null, ListState.Failed("disk full")))
        ),
        PhoneScreen(
            "10-settings",
            "Settings",
            "Settings",
            home(settings = { SettingsScreen(settingsState, onSave = {}, onAlertPrefs = {}) })
        ),
        PhoneScreen("11-ask", "Ask the watch a question", "Ask", home(ask = { PromptComposeScreen(ask, askActions) })),
        PhoneScreen(
            "12-tasks",
            "Tasks",
            "Tasks",
            home(
                tasks = {
                    TaskListScreen(
                        ListState.Content(listOf(Task("t-1", "Take medication"), Task("t-2", "Stand up and stretch"))),
                        onAdd = {},
                        onOpen = {},
                        onDelete = {},
                        onRetry = {}
                    )
                }
            )
        ),
        PhoneScreen(
            "13-tasks-empty",
            "Tasks: empty",
            "Tasks",
            home(tasks = { TaskListScreen(ListState.Empty, onAdd = {}, onOpen = {}, onDelete = {}, onRetry = {}) })
        ),
        PhoneScreen("14-alert", "Alert from the watch", null) { AlertScreen(138.0, 110.0, bright = true, onOk = {}) }
    )

    /** The page published next to the images. */
    fun indexMarkdown(): String = buildString {
        appendLine("# Phone screens")
        appendLine()
        appendLine("Rendered from the real Compose code with fixed example data (`PhoneScreensTest`).")
        for (screen in all) {
            appendLine()
            appendLine("## ${screen.title}")
            appendLine()
            appendLine("![${screen.title}](${screen.id}.png)")
        }
    }
}

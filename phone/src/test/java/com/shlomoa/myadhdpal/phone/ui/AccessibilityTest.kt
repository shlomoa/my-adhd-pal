package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.alert.AlertPrefs
import com.shlomoa.myadhdpal.phone.alert.AlertScreen
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.data.PromptError
import com.shlomoa.myadhdpal.phone.data.SettingsDefaults
import com.shlomoa.myadhdpal.phone.data.SettingsError
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.ui.history.HistoryScreen
import com.shlomoa.myadhdpal.phone.ui.history.HistoryUiState
import com.shlomoa.myadhdpal.phone.ui.link.LastSync
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateScreen
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateUiState
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptComposeActions
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptComposeScreen
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptComposeUiState
import com.shlomoa.myadhdpal.phone.ui.reminders.ReminderEditScreen
import com.shlomoa.myadhdpal.phone.ui.reminders.ReminderEditUiState
import com.shlomoa.myadhdpal.phone.ui.reminders.ReminderError
import com.shlomoa.myadhdpal.phone.ui.settings.SettingsScreen
import com.shlomoa.myadhdpal.phone.ui.settings.SettingsUiState
import com.shlomoa.myadhdpal.phone.ui.tasks.TaskEditScreen
import com.shlomoa.myadhdpal.phone.ui.tasks.TaskEditUiState
import com.shlomoa.myadhdpal.phone.ui.tasks.TaskListScreen
import com.shlomoa.myadhdpal.phone.ui.tasks.TitleError
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.HeartRatePermission
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.WatchStatus
import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Step 4.6, basic accessibility, for the screens that exist. Each screen added by steps 4.1 to 4.4 joins [screens].
 * TC-A-058: every actionable node has a label. TC-A-059: every actionable node is at least 48 dp square.
 */
@RunWith(RobolectricTestRunner::class)
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val screens: Map<String, @Composable () -> Unit> = mapOf(
        "link state" to {
            LinkStateScreen(LinkStateUiState(LinkState.NEARBY, LastSync.Ago(Duration.ofMinutes(2))))
        },
        "history content" to {
            HistoryScreen(
                HistoryUiState(
                    null,
                    ListState.Content(listOf(HistoryEntry.Acknowledged("a-1", 1_000, AckAction.DONE)))
                ),
                onFilter = {},
                onRetry = {}
            )
        },
        "history filtered" to {
            HistoryScreen(HistoryUiState(HistoryType.ALERT, ListState.Empty), onFilter = {}, onRetry = {})
        },
        "task list content" to {
            TaskListScreen(
                ListState.Content(listOf(Task("t-1", "Take the pills"))),
                onAdd = {},
                onOpen = {},
                onDelete = {},
                onRetry = {}
            )
        },
        "task list failed" to {
            TaskListScreen(ListState.Failed("disk"), onAdd = {}, onOpen = {}, onDelete = {}, onRetry = {})
        },
        "task edit with error" to {
            TaskEditScreen(TaskEditUiState("", TitleError.BLANK, false), onTitle = {}, onSave = {}, onCancel = {})
        },
        "reminder edit with errors" to {
            ReminderEditScreen(
                ReminderEditUiState(
                    null,
                    emptyList(),
                    setOf(ReminderError.TIME_MISSING, ReminderError.DAYS_INVALID),
                    false
                ),
                onTime = {},
                onDays = {},
                onSave = {},
                onDelete = {},
                onCancel = {}
            )
        },
        "alert bright" to { AlertScreen(138.0, 110.0, bright = true, onOk = {}) },
        "alert dark" to { AlertScreen(138.0, 110.0, bright = false, onOk = {}) },
        "settings with watch status" to {
            SettingsScreen(
                SettingsUiState(
                    SettingsDefaults.settings,
                    setOf(SettingsError.OFFSET_OUT_OF_RANGE),
                    WatchStatus(62, 92, HeartRatePermission.GRANTED),
                    AlertPrefs()
                ),
                onSave = {},
                onAlertPrefs = {}
            )
        },
        "settings before the watch reports" to {
            SettingsScreen(
                SettingsUiState(SettingsDefaults.settings, emptySet(), null, AlertPrefs()),
                onSave = {},
                onAlertPrefs = {}
            )
        },
        "ask with errors" to {
            PromptComposeScreen(
                PromptComposeUiState(
                    options = listOf("Yes", "", ""),
                    expiryMinutes = 15,
                    errors = setOf(PromptError.BLANK_TITLE, PromptError.TOO_FEW_OPTIONS)
                ),
                PromptComposeActions({}, { _, _ -> }, {}, {}, {}, {})
            )
        },
        "home with tabs" to {
            HomeScreen(
                link = LinkStateUiState(LinkState.AWAY, LastSync.Never),
                history = HistoryUiState(null, ListState.Empty),
                onFilter = {},
                onRetry = {},
                tasks = {},
                settings = {},
                ask = {}
            )
        },
        "history failed" to {
            HistoryScreen(HistoryUiState(null, ListState.Failed("disk")), onFilter = {}, onRetry = {})
        }
    )

    private var current by mutableStateOf<@Composable () -> Unit>({})

    /** Shows each screen in turn (`setContent` may be called once per test) and checks its actionable nodes. */
    private fun forEachScreen(check: (name: String, nodes: List<SemanticsNode>) -> Unit) {
        compose.setContent { MaterialTheme { current() } }
        for ((name, content) in screens) {
            current = content
            compose.waitForIdle()
            check(name, actionable().fetchSemanticsNodes())
        }
    }

    private fun actionable(): SemanticsNodeInteractionCollection = compose.onAllNodes(hasClickAction())

    @Test
    fun `TC-A-058 every actionable node has a label`() {
        forEachScreen { name, nodes ->
            for (node in nodes) {
                val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
                val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
                assertTrue("$name: a clickable node has no text or description", (text + description).isNotBlank())
                assertTrue("$name: node has a click action", node.config.contains(SemanticsActions.OnClick))
            }
        }
    }

    @Test
    fun `TC-A-059 every actionable node is at least 48 dp`() {
        val min = with(compose.density) { 48.dp.toPx() }
        forEachScreen { name, nodes ->
            for (node in nodes) {
                // The touch area, not the drawn size: Material enlarges small controls to the minimum touch target.
                // A node scrolled out of view has an empty touch area (its scroll container clips it); for that one
                // the layout size is all there is to measure.
                val touch = node.touchBoundsInRoot
                val width = if (touch.isEmpty) node.size.width.toFloat() else touch.width
                val height = if (touch.isEmpty) node.size.height.toFloat() else touch.height
                assertTrue("$name: touch target ${width}x${height}px is under 48 dp", height >= min && width >= min)
            }
        }
    }
}

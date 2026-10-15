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
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.ui.history.HistoryScreen
import com.shlomoa.myadhdpal.phone.ui.history.HistoryUiState
import com.shlomoa.myadhdpal.phone.ui.link.LastSync
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateScreen
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateUiState
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.LinkState
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
        "home with tabs" to {
            HomeScreen(
                link = LinkStateUiState(LinkState.AWAY, LastSync.Never),
                history = HistoryUiState(null, ListState.Empty),
                onFilter = {},
                onRetry = {}
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
                val touch = node.touchBoundsInRoot
                assertTrue("$name: touch target ${touch.width}x${touch.height}px is under 48 dp", touch.height >= min)
                assertTrue("$name: touch target ${touch.width}x${touch.height}px is under 48 dp", touch.width >= min)
            }
        }
    }
}

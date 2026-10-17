package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shlomoa.myadhdpal.phone.R
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.ui.history.HistoryScreen
import com.shlomoa.myadhdpal.phone.ui.history.HistoryUiState
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateScreen
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateUiState

private const val TAB_LINK = 0
private const val TAB_TASKS = 1
private const val TAB_SETTINGS = 2
private const val TAB_HISTORY = 3

/**
 * Stateless apart from the selected tab. [tasks] and [settings] are the Tasks and Settings tabs; later screens of
 * steps 4.1 to 4.4 add tabs.
 */
@Composable
fun HomeScreen(
    link: LinkStateUiState,
    history: HistoryUiState,
    onFilter: (HistoryType?) -> Unit,
    onRetry: () -> Unit,
    tasks: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_LINK) }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == TAB_LINK,
                    onClick = { tab = TAB_LINK },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_link)) }
                )
                NavigationBarItem(
                    selected = tab == TAB_TASKS,
                    onClick = { tab = TAB_TASKS },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_tasks)) }
                )
                NavigationBarItem(
                    selected = tab == TAB_SETTINGS,
                    onClick = { tab = TAB_SETTINGS },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
                NavigationBarItem(
                    selected = tab == TAB_HISTORY,
                    onClick = { tab = TAB_HISTORY },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_history)) }
                )
            }
        }
    ) { padding: PaddingValues ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                TAB_TASKS -> tasks()
                TAB_SETTINGS -> settings()
                TAB_HISTORY -> HistoryScreen(history, onFilter, onRetry)
                else -> LinkStateScreen(link)
            }
        }
    }
}

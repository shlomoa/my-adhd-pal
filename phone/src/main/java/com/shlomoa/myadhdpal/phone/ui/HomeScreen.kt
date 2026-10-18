package com.shlomoa.myadhdpal.phone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.shlomoa.myadhdpal.phone.ui.permissions.AlertPermissions
import com.shlomoa.myadhdpal.phone.ui.permissions.AlertPermissionsCard

/**
 * Stateless apart from the selected tab. [tasks] and [settings] are the Tasks and Settings tabs; later screens of
 * steps 4.1 to 4.4 add tabs. The Watch tab starts with the card that asks for the missing alert permissions.
 */
@Composable
fun HomeScreen(
    link: LinkStateUiState,
    history: HistoryUiState,
    onFilter: (HistoryType?) -> Unit,
    onRetry: () -> Unit,
    tasks: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    ask: @Composable () -> Unit,
    alertPermissions: AlertPermissions,
    onAllowNotifications: () -> Unit,
    onAllowFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: HomeTab = HomeTab.WATCH
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == HomeTab.WATCH,
                    onClick = { tab = HomeTab.WATCH },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_link)) }
                )
                NavigationBarItem(
                    selected = tab == HomeTab.TASKS,
                    onClick = { tab = HomeTab.TASKS },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_tasks)) }
                )
                NavigationBarItem(
                    selected = tab == HomeTab.ASK,
                    onClick = { tab = HomeTab.ASK },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_ask)) }
                )
                NavigationBarItem(
                    selected = tab == HomeTab.SETTINGS,
                    onClick = { tab = HomeTab.SETTINGS },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
                NavigationBarItem(
                    selected = tab == HomeTab.HISTORY,
                    onClick = { tab = HomeTab.HISTORY },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_history)) }
                )
            }
        }
    ) { padding: PaddingValues ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                HomeTab.TASKS -> tasks()

                HomeTab.ASK -> ask()

                HomeTab.SETTINGS -> settings()

                HomeTab.HISTORY -> HistoryScreen(history, onFilter, onRetry)

                else -> Column {
                    AlertPermissionsCard(alertPermissions, onAllowNotifications, onAllowFullScreen)
                    LinkStateScreen(link)
                }
            }
        }
    }
}

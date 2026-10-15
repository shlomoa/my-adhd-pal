package com.shlomoa.myadhdpal.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import com.shlomoa.myadhdpal.phone.ui.HomeScreen
import com.shlomoa.myadhdpal.phone.ui.history.HistoryViewModel
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val linkState: LinkStateViewModel by viewModels()
    private val history: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeScreen(
                    link = linkState.uiState.collectAsState().value,
                    history = history.uiState.collectAsState().value,
                    onFilter = history::setFilter,
                    onRetry = history::retry
                )
            }
        }
    }
}

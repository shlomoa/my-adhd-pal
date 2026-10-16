package com.shlomoa.myadhdpal.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.core.os.bundleOf
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.shlomoa.myadhdpal.phone.ui.HomeScreen
import com.shlomoa.myadhdpal.phone.ui.history.HistoryViewModel
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateViewModel
import com.shlomoa.myadhdpal.phone.ui.settings.SettingsScreen
import com.shlomoa.myadhdpal.phone.ui.settings.SettingsViewModel
import com.shlomoa.myadhdpal.phone.ui.tasks.TaskEditViewModel
import com.shlomoa.myadhdpal.phone.ui.tasks.TaskListViewModel
import com.shlomoa.myadhdpal.phone.ui.tasks.TasksTab
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val linkState: LinkStateViewModel by viewModels()
    private val history: HistoryViewModel by viewModels()
    private val taskList: TaskListViewModel by viewModels()
    private val settings: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeScreen(
                    link = linkState.uiState.collectAsState().value,
                    history = history.uiState.collectAsState().value,
                    onFilter = history::setFilter,
                    onRetry = history::retry,
                    tasks = { TasksTab(taskList, ::taskEditor) },
                    settings = {
                        SettingsScreen(
                            state = settings.uiState.collectAsState().value,
                            onSave = settings::save,
                            onAlertPrefs = settings::setAlertPrefs
                        )
                    }
                )
            }
        }
    }

    /** One edit ViewModel per edit session, started with the task id as its `SavedStateHandle` argument. */
    private fun taskEditor(session: Int, taskId: String?): TaskEditViewModel {
        val extras = MutableCreationExtras(defaultViewModelCreationExtras)
        extras[DEFAULT_ARGS_KEY] = bundleOf("taskId" to taskId)
        return ViewModelProvider.create(this, defaultViewModelProviderFactory, extras)
            .get("task-edit-$session", TaskEditViewModel::class.java)
    }
}

package com.shlomoa.myadhdpal.phone

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.shlomoa.myadhdpal.phone.ui.HomeScreen
import com.shlomoa.myadhdpal.phone.ui.HomeTab
import com.shlomoa.myadhdpal.phone.ui.history.HistoryViewModel
import com.shlomoa.myadhdpal.phone.ui.link.LinkStateViewModel
import com.shlomoa.myadhdpal.phone.ui.permissions.AlertPermissions
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptComposeActions
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptComposeScreen
import com.shlomoa.myadhdpal.phone.ui.prompts.PromptComposeViewModel
import com.shlomoa.myadhdpal.phone.ui.reminders.ReminderEditViewModel
import com.shlomoa.myadhdpal.phone.ui.reminders.TaskRemindersViewModel
import com.shlomoa.myadhdpal.phone.ui.schedule.ScheduleSyncViewModel
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
    private val scheduleSync: ScheduleSyncViewModel by viewModels()
    private val promptCompose: PromptComposeViewModel by viewModels()

    private var alertPermissions by mutableStateOf(AlertPermissions(notifications = true, fullScreen = true))

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshAlertPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshAlertPermissions()
    }

    /** Read again after returning from the system dialog or settings page, where the user may have changed them. */
    private fun refreshAlertPermissions() {
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val canUseFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        alertPermissions = AlertPermissions.of(Build.VERSION.SDK_INT, notificationsGranted, canUseFullScreen)
    }

    private fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun allowFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", packageName, null))
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeScreen(
                    link = linkState.uiState.collectAsState().value,
                    history = history.uiState.collectAsState().value,
                    onFilter = history::setFilter,
                    onRetry = history::retry,
                    alertPermissions = alertPermissions,
                    onAllowNotifications = ::allowNotifications,
                    onAllowFullScreen = ::allowFullScreen,
                    initialTab = HomeTab.named(intent.getStringExtra(EXTRA_TAB)),
                    tasks = { TasksTab(taskList, scheduleSync, ::taskEditor, ::taskReminders, ::reminderEditor) },
                    settings = {
                        SettingsScreen(
                            state = settings.uiState.collectAsState().value,
                            onSave = settings::save,
                            onAlertPrefs = settings::setAlertPrefs
                        )
                    },
                    ask = {
                        PromptComposeScreen(
                            state = promptCompose.uiState.collectAsState().value,
                            actions = PromptComposeActions(
                                onTitle = promptCompose::setTitle,
                                onOption = promptCompose::setOption,
                                onAddOption = promptCompose::addOption,
                                onRemoveOption = promptCompose::removeOption,
                                onExpiry = promptCompose::setExpiry,
                                onSend = promptCompose::send
                            )
                        )
                    }
                )
            }
        }
    }

    private companion object {
        /** `adb shell am start -n ... --es tab history` opens that tab (the phone walkthrough script uses it). */
        const val EXTRA_TAB = "tab"
    }

    /** One edit ViewModel per edit session, started with the task id as its `SavedStateHandle` argument. */
    private fun taskEditor(session: Int, taskId: String?): TaskEditViewModel =
        viewModelWith("task-edit-$session", "taskId" to taskId)

    /** The reminders of one task, for its form. */
    private fun taskReminders(taskId: String): TaskRemindersViewModel =
        viewModelWith("task-reminders-$taskId", "taskId" to taskId)

    /** One reminder form per edit session, for a new reminder of the task or the one named. */
    private fun reminderEditor(session: Int, taskId: String, reminderId: String?): ReminderEditViewModel =
        viewModelWith("reminder-edit-$session", "taskId" to taskId, "reminderId" to reminderId)

    /** A Hilt ViewModel stored under [key], started with [args] as its `SavedStateHandle` arguments. */
    private inline fun <reified T : ViewModel> viewModelWith(key: String, vararg args: Pair<String, String?>): T {
        val extras = MutableCreationExtras(defaultViewModelCreationExtras)
        extras[DEFAULT_ARGS_KEY] = bundleOf(*args)
        return ViewModelProvider.create(this, defaultViewModelProviderFactory, extras).get(key, T::class.java)
    }
}

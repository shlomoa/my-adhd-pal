package com.shlomoa.myadhdpal.phone.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shlomoa.myadhdpal.phone.data.PublishOutcome
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the last tap on "Send schedule to the watch" did. */
sealed interface SyncStatus {
    data object Idle : SyncStatus

    data object Sending : SyncStatus

    data class Sent(val version: Long) : SyncStatus

    data object UpToDate : SyncStatus

    data class Failed(val reason: String) : SyncStatus
}

data class ScheduleSyncUiState(val lastVersion: Long?, val status: SyncStatus)

/** Step 4.2: sends the schedule built from the tasks, reminders and settings, and says what happened. */
@HiltViewModel
class ScheduleSyncViewModel @Inject constructor(private val schedule: ScheduleRepository) : ViewModel() {
    private val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    val uiState: StateFlow<ScheduleSyncUiState> = combine(schedule.lastPublishedVersion, status, ::ScheduleSyncUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScheduleSyncUiState(null, SyncStatus.Idle))

    fun send() {
        if (status.value == SyncStatus.Sending) return
        status.value = SyncStatus.Sending
        viewModelScope.launch {
            status.value = when (val outcome = schedule.publish()) {
                is PublishOutcome.Published -> SyncStatus.Sent(outcome.version)
                PublishOutcome.Unchanged -> SyncStatus.UpToDate
                is PublishOutcome.Failed -> SyncStatus.Failed(outcome.reason)
            }
        }
    }
}

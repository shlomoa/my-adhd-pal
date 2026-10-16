package com.shlomoa.myadhdpal.phone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shlomoa.myadhdpal.phone.alert.AlertPreferences
import com.shlomoa.myadhdpal.phone.alert.AlertPrefs
import com.shlomoa.myadhdpal.phone.data.AppSettings
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.data.SettingsDefaults
import com.shlomoa.myadhdpal.phone.data.SettingsError
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.SettingsResult
import com.shlomoa.myadhdpal.phone.data.WatchStatusRepository
import com.shlomoa.myadhdpal.protocol.WatchStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the settings screen shows. [errors] are those of the last save (empty when it succeeded); [watchStatus] is null
 * until the watch has reported its baseline, threshold and heart-rate permission.
 */
data class SettingsUiState(
    val settings: AppSettings,
    val errors: Set<SettingsError>,
    val watchStatus: WatchStatus?,
    val alert: AlertPrefs
)

/**
 * Step 4.3: the settings the watch needs (alert offset and initial threshold, cooldown, cadence) and the phone's alert
 * switches. A save that passes validation is published to the watch as a new schedule version; one that does not
 * changes nothing and reports why.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val schedule: ScheduleRepository,
    watchStatus: WatchStatusRepository,
    private val alertPreferences: AlertPreferences
) : ViewModel() {
    private val errors = MutableStateFlow<Set<SettingsError>>(emptySet())

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.settings,
        errors,
        watchStatus.status,
        alertPreferences.prefs
    ) { saved, errors, status, alert -> SettingsUiState(saved, errors, status, alert) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsUiState(SettingsDefaults.settings, emptySet(), null, AlertPrefs())
        )

    fun save(newSettings: AppSettings) {
        viewModelScope.launch {
            when (val result = settings.update(newSettings)) {
                SettingsResult.Saved -> {
                    errors.value = emptySet()
                    schedule.publish()
                }

                is SettingsResult.Invalid -> errors.value = result.errors
            }
        }
    }

    fun setAlertPrefs(prefs: AlertPrefs) {
        viewModelScope.launch { alertPreferences.update(prefs) }
    }
}

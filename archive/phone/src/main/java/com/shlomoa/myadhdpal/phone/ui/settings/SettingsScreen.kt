package com.shlomoa.myadhdpal.phone.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.R
import com.shlomoa.myadhdpal.phone.alert.AlertPrefs
import com.shlomoa.myadhdpal.phone.data.AppSettings
import com.shlomoa.myadhdpal.phone.data.SettingsError
import com.shlomoa.myadhdpal.phone.data.SettingsLimits
import com.shlomoa.myadhdpal.protocol.HeartRatePermission
import com.shlomoa.myadhdpal.protocol.WatchStatus

/** Stateless apart from the text being typed. A number that is not a whole number never reaches [onSave]. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSave: (AppSettings) -> Unit,
    onAlertPrefs: (AlertPrefs) -> Unit,
    modifier: Modifier = Modifier
) {
    val saved = state.settings
    var offset by rememberSaveable(saved) { mutableStateOf(saved.offsetBpm.toString()) }
    var initial by rememberSaveable(saved) { mutableStateOf(saved.initialBpm.toString()) }
    var cooldown by rememberSaveable(saved) { mutableStateOf(saved.cooldownSeconds.toString()) }
    var cadence by rememberSaveable(saved) { mutableStateOf(saved.cadenceMinutes.toString()) }
    var notNumbers by remember { mutableStateOf(emptySet<String>()) }
    val errors = state.errors

    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WatchStatusBlock(state.watchStatus)
        val numberError = { key: String -> key in notNumbers }
        NumberField(
            R.string.settings_offset,
            offset,
            { offset = it },
            numberError("offset"),
            SettingsError.OFFSET_OUT_OF_RANGE in errors,
            SettingsLimits.OFFSET_BPM
        )
        NumberField(
            R.string.settings_initial,
            initial,
            { initial = it },
            numberError("initial"),
            SettingsError.INITIAL_OUT_OF_RANGE in errors,
            SettingsLimits.INITIAL_BPM
        )
        NumberField(
            R.string.settings_cooldown,
            cooldown,
            { cooldown = it },
            numberError("cooldown"),
            SettingsError.COOLDOWN_OUT_OF_RANGE in errors,
            SettingsLimits.COOLDOWN_SECONDS
        )
        NumberField(
            R.string.settings_cadence,
            cadence,
            { cadence = it },
            numberError("cadence"),
            SettingsError.CADENCE_OUT_OF_RANGE in errors,
            SettingsLimits.CADENCE_MINUTES
        )
        Button(onClick = {
            val (parsed, bad) = saved.withInput(offset, initial, cooldown, cadence)
            notNumbers = bad
            parsed?.let(onSave)
        }) { Text(stringResource(R.string.settings_save)) }
        AlertSwitches(state.alert, onAlertPrefs)
    }
}

/** The settings with the typed numbers, or null and the names of the fields that are not whole numbers. */
private fun AppSettings.withInput(
    offset: String,
    initial: String,
    cooldown: String,
    cadence: String
): Pair<AppSettings?, Set<String>> {
    val numbers = mapOf(
        "offset" to offset.trim().toIntOrNull(),
        "initial" to initial.trim().toIntOrNull(),
        "cooldown" to cooldown.trim().toIntOrNull(),
        "cadence" to cadence.trim().toIntOrNull()
    )
    val bad = numbers.filterValues { it == null }.keys
    if (bad.isNotEmpty()) return null to bad
    return copy(
        offsetBpm = numbers.getValue("offset")!!,
        initialBpm = numbers.getValue("initial")!!,
        cooldownSeconds = numbers.getValue("cooldown")!!,
        cadenceMinutes = numbers.getValue("cadence")!!
    ) to emptySet()
}

@Composable
private fun AlertSwitches(alert: AlertPrefs, onAlertPrefs: (AlertPrefs) -> Unit) {
    Text(stringResource(R.string.settings_alert_section), style = MaterialTheme.typography.titleMedium)
    SwitchRow(stringResource(R.string.settings_alert_sound), alert.soundOn) { onAlertPrefs(alert.copy(soundOn = it)) }
    SwitchRow(stringResource(R.string.settings_alert_flash), alert.flashOn) { onAlertPrefs(alert.copy(flashOn = it)) }
}

/** A number field. Under it: "not a whole number", or "outside [range]" after a save the repository refused. */
@Composable
private fun NumberField(
    @StringRes label: Int,
    value: String,
    onChange: (String) -> Unit,
    notNumber: Boolean,
    outOfRange: Boolean,
    range: IntRange
) {
    val error = when {
        notNumber -> stringResource(R.string.settings_error_number)
        outOfRange -> stringResource(R.string.settings_error_range, range.first, range.last)
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(
            checked,
            role = Role.Switch,
            onValueChange = onChange
        ).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun WatchStatusBlock(status: WatchStatus?) {
    Text(stringResource(R.string.settings_watch_section), style = MaterialTheme.typography.titleMedium)
    if (status == null) {
        Text(stringResource(R.string.settings_watch_none))
        return
    }
    val unknown = stringResource(R.string.settings_watch_unknown)
    val permission = stringResource(
        when (status.heartRatePermission) {
            HeartRatePermission.GRANTED -> R.string.settings_watch_granted
            HeartRatePermission.DENIED -> R.string.settings_watch_denied
            HeartRatePermission.UNKNOWN -> R.string.settings_watch_unknown
        }
    )
    Text(stringResource(R.string.settings_watch_baseline, status.baselineBpm?.toString() ?: unknown))
    Text(stringResource(R.string.settings_watch_threshold, status.thresholdBpm?.toString() ?: unknown))
    Text(stringResource(R.string.settings_watch_permission, permission))
}

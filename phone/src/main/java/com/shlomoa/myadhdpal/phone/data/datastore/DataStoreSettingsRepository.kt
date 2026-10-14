package com.shlomoa.myadhdpal.phone.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shlomoa.myadhdpal.phone.data.AppSettings
import com.shlomoa.myadhdpal.phone.data.SettingsDefaults
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.SettingsResult
import com.shlomoa.myadhdpal.phone.data.SettingsValidator
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import com.shlomoa.myadhdpal.protocol.Threshold
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * [SettingsRepository] on Preferences DataStore. Values are validated before they are written; a missing or damaged
 * value falls back to the default of [SettingsDefaults], so a bad file never crashes the app.
 */
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::read)

    override suspend fun update(settings: AppSettings): SettingsResult {
        val errors = SettingsValidator.validate(settings)
        if (errors.isNotEmpty()) return SettingsResult.Invalid(errors)
        store.edit { prefs ->
            prefs[THRESHOLDS] = json.encodeToString(thresholdsSerializer, settings.thresholds)
            prefs[COOLDOWN_SECONDS] = settings.cooldownSeconds
            prefs[SNOOZE_MINUTES] = settings.snooze.minutes
            prefs[SNOOZE_MAX_COUNT] = settings.snooze.maxCount
            prefs[CADENCE_MINUTES] = settings.cadenceMinutes
        }
        return SettingsResult.Saved
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = SettingsDefaults.settings
        val thresholds = prefs[THRESHOLDS]?.let {
            runCatching { json.decodeFromString(thresholdsSerializer, it) }.getOrNull()
        } ?: defaults.thresholds
        val snooze = runCatching {
            SnoozeSettings(
                prefs[SNOOZE_MINUTES] ?: defaults.snooze.minutes,
                prefs[SNOOZE_MAX_COUNT] ?: defaults.snooze.maxCount
            )
        }.getOrDefault(defaults.snooze)
        return AppSettings(
            thresholds = thresholds,
            cooldownSeconds = prefs[COOLDOWN_SECONDS] ?: defaults.cooldownSeconds,
            snooze = snooze,
            cadenceMinutes = prefs[CADENCE_MINUTES] ?: defaults.cadenceMinutes
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val thresholdsSerializer = ListSerializer(Threshold.serializer())
        val THRESHOLDS = stringPreferencesKey("thresholds")
        val COOLDOWN_SECONDS = intPreferencesKey("cooldown_seconds")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val SNOOZE_MAX_COUNT = intPreferencesKey("snooze_max_count")
        val CADENCE_MINUTES = intPreferencesKey("cadence_minutes")
    }
}

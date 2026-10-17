package com.shlomoa.myadhdpal.phone.alert

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The alert switches the user can change. */
interface AlertPreferences {
    val prefs: Flow<AlertPrefs>

    suspend fun update(prefs: AlertPrefs)
}

/** The alert switches on Preferences DataStore. Missing values read as the defaults: sound and flashing on. */
class DataStoreAlertPreferences(private val store: DataStore<Preferences>) : AlertPreferences {
    override val prefs: Flow<AlertPrefs> = store.data.map {
        AlertPrefs(soundOn = it[SOUND] ?: true, flashOn = it[FLASH] ?: true)
    }

    override suspend fun update(prefs: AlertPrefs) {
        store.edit {
            it[SOUND] = prefs.soundOn
            it[FLASH] = prefs.flashOn
        }
    }

    private companion object {
        val SOUND = booleanPreferencesKey("sound_on")
        val FLASH = booleanPreferencesKey("flash_on")
    }
}

/** The raised alert ids, one per line in the order raised (the ids are UUIDs and hold no line break). */
class DataStoreRaisedAlerts(private val store: DataStore<Preferences>) : RaisedAlertsStore {
    override suspend fun load(): Set<String>? =
        store.data.first()[RAISED]?.lineSequence()?.filter { it.isNotEmpty() }?.toCollection(LinkedHashSet())

    override suspend fun save(ids: Set<String>) {
        store.edit { it[RAISED] = ids.joinToString("\n") }
    }

    private companion object {
        val RAISED = stringPreferencesKey("raised_alert_ids")
    }
}

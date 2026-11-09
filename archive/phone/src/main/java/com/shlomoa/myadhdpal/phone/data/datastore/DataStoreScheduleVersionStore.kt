package com.shlomoa.myadhdpal.phone.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shlomoa.myadhdpal.phone.data.ScheduleVersionStore
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Schedule
import kotlinx.coroutines.flow.first

/**
 * Remembers the last published schedule on Preferences DataStore, as the same JSON body that goes on the wire, so the
 * next version is always above the last one across restarts. A damaged value reads as "nothing published yet".
 */
class DataStoreScheduleVersionStore(private val store: DataStore<Preferences>) : ScheduleVersionStore {
    override suspend fun lastVersion(): Long? = lastSchedule()?.version

    override suspend fun lastSchedule(): Schedule? {
        val body = store.data.first()[LAST_SCHEDULE] ?: return null
        val decoded = Codec.decode(Paths.SCHEDULE, Envelope(Protocol.MAJOR_VERSION, STORED_ID, 0L, body))
        return (decoded as? DecodeResult.Ok)?.item?.message as? Schedule
    }

    override suspend fun save(schedule: Schedule) {
        store.edit { it[LAST_SCHEDULE] = Codec.encodeBody(schedule) }
    }

    private companion object {
        val LAST_SCHEDULE = stringPreferencesKey("last_schedule")
        const val STORED_ID = "last-published"
    }
}

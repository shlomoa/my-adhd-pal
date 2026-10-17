package com.shlomoa.myadhdpal.phone.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreScheduleVersionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelScopes() = scopes.forEach { it.cancel() }

    private fun open(): DataStoreScheduleVersionStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val file = File(folder.root, "schedule.preferences_pb")
        return DataStoreScheduleVersionStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    }

    private fun schedule(version: Long) = Schedule(
        version,
        listOf(Reminder("r1", "Meds", 8 * 60, listOf(1, 2, 3))),
        emptyList(),
        SnoozeSettings(10, 3),
        AlertSettings(600)
    )

    @Test
    fun `nothing is stored at first`() = runTest {
        val store = open()
        assertNull(store.lastVersion())
        assertNull(store.lastSchedule())
    }

    @Test
    fun `a saved schedule and its version survive a new store on the same file`() = runTest {
        open().save(schedule(7))
        // DataStore allows one active instance per file and frees it when the scope's job has completed.
        scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
        scopes.clear()
        val reopened = open()
        assertEquals(7L, reopened.lastVersion())
        assertEquals(schedule(7), reopened.lastSchedule())
    }

    @Test
    fun `saving again replaces the schedule`() = runTest {
        val store = open()
        store.save(schedule(1))
        store.save(schedule(2))
        assertEquals(2L, store.lastVersion())
    }
}

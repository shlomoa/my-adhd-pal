package com.shlomoa.myadhdpal.phone.alert

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlertStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelScopes() = scopes.forEach { it.cancel() }

    private fun open() = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it },
        produceFile = { File(folder.root, "alert.preferences_pb") }
    )

    @Test
    fun `TC-A-102 preferences default to sound and flashing on and a change is kept`() = runTest {
        val prefs = DataStoreAlertPreferences(open())
        assertEquals(AlertPrefs(soundOn = true, flashOn = true), prefs.prefs.first())
        prefs.update(AlertPrefs(soundOn = false, flashOn = true))
        assertEquals(AlertPrefs(soundOn = false, flashOn = true), prefs.prefs.first())
    }

    @Test
    fun `TC-A-102 the raised alert ids are kept, and nothing is stored at first`() = runTest {
        val store = DataStoreRaisedAlerts(open())
        assertNull(store.load())
        store.save(setOf("a-1", "a-2"))
        assertEquals(setOf("a-1", "a-2"), store.load())
    }
}

package com.shlomoa.myadhdpal.phone.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.shlomoa.myadhdpal.phone.data.SettingsDefaults
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.SettingsResult
import com.shlomoa.myadhdpal.phone.data.contract.SettingsRepositoryContract
import com.shlomoa.myadhdpal.protocol.Direction
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import com.shlomoa.myadhdpal.protocol.Threshold
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** TC-S-098 and TC-S-099, plus TC-S-092: the DataStore repository passes the same contract as the in-memory fake. */
class DataStoreSettingsRepositoryTest : SettingsRepositoryContract() {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private lateinit var file: File

    @Before
    fun prepare() {
        file = File(folder.root, "settings.preferences_pb")
    }

    @After
    fun cancelScopes() {
        scopes.forEach { it.cancel() }
    }

    /**
     * DataStore allows one active instance per file and frees the file only when its scope's job has completed, which
     * `cancel()` alone does not wait for: reopening right after it can fail with "multiple DataStores active".
     */
    private suspend fun closeAll() {
        scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
        scopes.clear()
    }

    private fun open(): SettingsRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return DataStoreSettingsRepository(store)
    }

    override fun create(): SettingsRepository = open()

    @Test
    fun `TC-S-098 a fresh store has the documented defaults for thresholds, cooldown, snooze and cadence`() = runTest {
        val settings = open().settings.first()
        assertEquals(SettingsDefaults.settings, settings)
        assertEquals(300, settings.cooldownSeconds)
        assertEquals(30, settings.offsetBpm)
        assertEquals(110, settings.initialBpm)
        assertEquals(60, settings.cadenceMinutes)
        assertEquals(listOf(Threshold(Metric.HEART_RATE, Direction.ABOVE, 150.0)), settings.thresholds)
    }

    @Test
    fun `TC-S-099 a written value is still there after the store is closed and opened again`() = runTest {
        val changed = SettingsDefaults.settings.copy(
            thresholds = listOf(
                Threshold(Metric.HEART_RATE, Direction.ABOVE, 140.5),
                Threshold(Metric.HEART_RATE, Direction.BELOW, 40.0)
            ),
            cooldownSeconds = 900,
            snooze = SnoozeSettings(minutes = 5, maxCount = 2),
            cadenceMinutes = 30,
            offsetBpm = 45,
            initialBpm = 125
        )
        assertEquals(SettingsResult.Saved, open().update(changed))
        closeAll()
        assertEquals(changed, open().settings.first())
    }

    @Test
    fun `TC-S-099 reopening the file right after closing it works every time`() = runTest {
        repeat(100) { round ->
            val value = SettingsDefaults.settings.copy(cooldownSeconds = 100 + round)
            assertEquals(SettingsResult.Saved, open().update(value))
            closeAll()
            assertEquals(value, open().settings.first())
            closeAll()
        }
    }

    @Test
    fun `TC-S-099 out-of-range values are rejected and nothing is written`() = runTest {
        val repo = open()
        repo.update(SettingsDefaults.settings.copy(cooldownSeconds = 900))
        val result = repo.update(SettingsDefaults.settings.copy(cooldownSeconds = 5, cadenceMinutes = 99_999))
        assertEquals(true, result is SettingsResult.Invalid)
        assertEquals(900, repo.settings.first().cooldownSeconds)
    }

    @Test
    fun `TC-S-099 a damaged file falls back to the defaults instead of crashing`() = runTest {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(SettingsDefaults.settings, open().settings.first())
    }
}

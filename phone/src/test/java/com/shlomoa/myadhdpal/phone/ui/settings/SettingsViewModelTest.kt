package com.shlomoa.myadhdpal.phone.ui.settings

import com.shlomoa.myadhdpal.phone.alert.AlertPreferences
import com.shlomoa.myadhdpal.phone.alert.AlertPrefs
import com.shlomoa.myadhdpal.phone.data.AppSettings
import com.shlomoa.myadhdpal.phone.data.SettingsDefaults
import com.shlomoa.myadhdpal.phone.data.SettingsError
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.WatchStatusRepository
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.fake.InMemorySettingsRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import com.shlomoa.myadhdpal.protocol.Direction
import com.shlomoa.myadhdpal.protocol.HeartRatePermission
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Threshold
import com.shlomoa.myadhdpal.protocol.WatchStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeWatchStatuses : WatchStatusRepository {
    val current = MutableStateFlow<WatchStatus?>(null)
    override val status: Flow<WatchStatus?> = current
}

private class InMemoryAlertPreferences : AlertPreferences {
    private val state = MutableStateFlow(AlertPrefs())
    override val prefs: Flow<AlertPrefs> = state

    override suspend fun update(prefs: AlertPrefs) {
        state.value = prefs
    }
}

/** Step 4.3: thresholds, cooldown, cadence, the alert offset, the watch status and the alert switches. */
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val tasks = InMemoryTaskRepository()
    private val settings = InMemorySettingsRepository()
    private val source = FakeWatchLinkDataSource()
    private val schedule = InMemoryScheduleRepository(tasks, settings, source, InMemoryScheduleVersionStore())
    private val statuses = FakeWatchStatuses()
    private val alertPrefs = InMemoryAlertPreferences()
    private val vm by lazy { SettingsViewModel(settings, schedule, statuses, alertPrefs) }
    private val defaults = SettingsDefaults.settings

    private val hr = { value: Double -> Threshold(Metric.HEART_RATE, Direction.ABOVE, value) }

    @Test
    fun `TC-A-031 defaults are shown on first run`() {
        assertEquals(defaults, vm.uiState.value.settings)
    }

    @Test
    fun `TC-A-032 change an alert threshold`() = runTest {
        vm.save(defaults.copy(thresholds = listOf(hr(140.0))))
        assertEquals(listOf(hr(140.0)), vm.uiState.value.settings.thresholds)
        assertTrue(vm.uiState.value.errors.isEmpty())
    }

    @Test
    fun `TC-A-033 a threshold out of range is rejected and the old value kept`() = runTest {
        vm.save(defaults.copy(thresholds = listOf(hr(500.0))))
        assertEquals(setOf(SettingsError.THRESHOLD_OUT_OF_RANGE), vm.uiState.value.errors)
        assertEquals(defaults.thresholds, vm.uiState.value.settings.thresholds)
    }

    @Test
    fun `TC-A-034 change the alert cooldown`() = runTest {
        vm.save(defaults.copy(cooldownSeconds = 1200))
        assertEquals(1200, vm.uiState.value.settings.cooldownSeconds)
    }

    @Test
    fun `TC-A-035 a cooldown out of range is rejected`() = runTest {
        vm.save(defaults.copy(cooldownSeconds = 5))
        assertEquals(setOf(SettingsError.COOLDOWN_OUT_OF_RANGE), vm.uiState.value.errors)
        assertEquals(defaults.cooldownSeconds, vm.uiState.value.settings.cooldownSeconds)
    }

    @Test
    fun `TC-A-036 change the reminder cadence`() = runTest {
        vm.save(defaults.copy(cadenceMinutes = 30))
        assertEquals(30, vm.uiState.value.settings.cadenceMinutes)
    }

    @Test
    fun `TC-A-037 a saved change publishes a new schedule version, an invalid one does not`() = runTest {
        tasks.save(Task("t-1", "Pills"))
        tasks.save(TaskReminder("r-1", "t-1", 480, listOf(1)))
        vm.save(defaults.copy(cooldownSeconds = 1200))
        assertEquals(1200, source.publishedSchedules.single().alerts.cooldownSeconds)
        vm.save(defaults.copy(cooldownSeconds = 5))
        assertEquals(1, source.publishedSchedules.size)
    }

    @Test
    fun `TC-A-110 the offset defaults to 30 and a change within 20 to 50 is saved and published`() = runTest {
        tasks.save(Task("t-1", "Pills"))
        tasks.save(TaskReminder("r-1", "t-1", 480, listOf(1)))
        assertEquals(30, vm.uiState.value.settings.offsetBpm)
        vm.save(defaults.copy(offsetBpm = 40))
        assertEquals(40, vm.uiState.value.settings.offsetBpm)
        assertEquals(40, source.publishedSchedules.single().alerts.offsetBpm)
        vm.save(defaults.copy(offsetBpm = 20))
        vm.save(defaults.copy(offsetBpm = 50))
        assertEquals(50, vm.uiState.value.settings.offsetBpm)
    }

    @Test
    fun `TC-A-111 an offset outside 20 to 50 is rejected, the old value kept and nothing published`() = runTest {
        tasks.save(Task("t-1", "Pills"))
        tasks.save(TaskReminder("r-1", "t-1", 480, listOf(1)))
        for (offset in listOf(19, 51)) {
            vm.save(defaults.copy(offsetBpm = offset))
            assertEquals(setOf(SettingsError.OFFSET_OUT_OF_RANGE), vm.uiState.value.errors)
            assertEquals(30, vm.uiState.value.settings.offsetBpm)
        }
        assertTrue(source.publishedSchedules.isEmpty())
    }

    @Test
    fun `TC-A-112 the initial threshold can be changed within its range and not outside it`() = runTest {
        vm.save(defaults.copy(initialBpm = 120))
        assertEquals(120, vm.uiState.value.settings.initialBpm)
        vm.save(defaults.copy(initialBpm = 10))
        assertEquals(setOf(SettingsError.INITIAL_OUT_OF_RANGE), vm.uiState.value.errors)
        assertEquals(120, vm.uiState.value.settings.initialBpm)
    }

    @Test
    fun `TC-A-113 the watch status is shown, and nothing before the watch has sent it`() = runTest {
        assertNull(vm.uiState.value.watchStatus)
        statuses.current.value =
            WatchStatus(baselineBpm = 62, thresholdBpm = 92, heartRatePermission = HeartRatePermission.GRANTED)
        assertEquals(62, vm.uiState.value.watchStatus?.baselineBpm)
        assertEquals(92, vm.uiState.value.watchStatus?.thresholdBpm)
        assertEquals(HeartRatePermission.GRANTED, vm.uiState.value.watchStatus?.heartRatePermission)
    }

    @Test
    fun `TC-A-114 the alert sound and flashing switches default to on and a change is kept`() = runTest {
        assertEquals(AlertPrefs(soundOn = true, flashOn = true), vm.uiState.value.alert)
        vm.setAlertPrefs(AlertPrefs(soundOn = false, flashOn = true))
        assertEquals(AlertPrefs(soundOn = false, flashOn = true), vm.uiState.value.alert)
        vm.setAlertPrefs(AlertPrefs(soundOn = false, flashOn = false))
        assertEquals(AlertPrefs(soundOn = false, flashOn = false), vm.uiState.value.alert)
    }

    @Test
    fun `a settings value that is not saved does not change the stored settings`() = runTest {
        val before: AppSettings = vm.uiState.value.settings
        vm.save(before.copy(cadenceMinutes = 1))
        assertEquals(before, vm.uiState.value.settings)
    }
}

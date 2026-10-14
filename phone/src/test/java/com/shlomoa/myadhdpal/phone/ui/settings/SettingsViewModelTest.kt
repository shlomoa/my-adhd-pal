package com.shlomoa.myadhdpal.phone.ui.settings

import com.shlomoa.myadhdpal.phone.data.SettingsDefaults
import com.shlomoa.myadhdpal.phone.data.SettingsError
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.fake.InMemorySettingsRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import com.shlomoa.myadhdpal.protocol.Direction
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Threshold
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Step 4.3: thresholds, cooldown and cadence. A saved change publishes a new schedule version. */
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val tasks = InMemoryTaskRepository()
    private val settings = InMemorySettingsRepository()
    private val source = FakeWatchLinkDataSource()
    private val schedule = InMemoryScheduleRepository(tasks, settings, source, InMemoryScheduleVersionStore())
    private val vm = SettingsViewModel(settings, schedule)
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
}

package com.shlomoa.myadhdpal.phone.data.contract

import com.shlomoa.myadhdpal.phone.data.AppSettings
import com.shlomoa.myadhdpal.phone.data.SettingsDefaults
import com.shlomoa.myadhdpal.phone.data.SettingsError
import com.shlomoa.myadhdpal.phone.data.SettingsLimits
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.SettingsResult
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every [SettingsRepository] must pass these. */
abstract class SettingsRepositoryContract {
    abstract fun create(): SettingsRepository

    private val valid = SettingsDefaults.settings.copy(cooldownSeconds = 900, cadenceMinutes = 30)

    @Test
    fun `TC-S-092 settings repository starts with the documented defaults`() = runTest {
        assertEquals(SettingsDefaults.settings, create().settings.first())
    }

    @Test
    fun `TC-S-164 settings have the decided alert defaults, offset 30, initial 110 and cooldown 300`() = runTest {
        val settings = create().settings.first()
        assertEquals(30, settings.offsetBpm)
        assertEquals(110, settings.initialBpm)
        assertEquals(300, settings.cooldownSeconds)
        assertEquals(20..50, SettingsLimits.OFFSET_BPM)
        assertEquals(30..220, SettingsLimits.INITIAL_BPM)
    }

    @Test
    fun `TC-S-164 settings save the offset and the initial threshold and reject values out of range`() = runTest {
        val repo = create()
        val changed = valid.copy(offsetBpm = 40, initialBpm = 120)
        assertEquals(SettingsResult.Saved, repo.update(changed))
        assertEquals(changed, repo.settings.first())
        for (bad in listOf(19, 51)) {
            assertEquals(
                SettingsResult.Invalid(setOf(SettingsError.OFFSET_OUT_OF_RANGE)),
                repo.update(changed.copy(offsetBpm = bad))
            )
        }
        for (bad in listOf(29, 221)) {
            assertEquals(
                SettingsResult.Invalid(setOf(SettingsError.INITIAL_OUT_OF_RANGE)),
                repo.update(changed.copy(initialBpm = bad))
            )
        }
        assertEquals(changed, repo.settings.first())
    }

    @Test
    fun `TC-S-092 settings repository saves a valid value and reads it back`() = runTest {
        val repo = create()
        assertEquals(SettingsResult.Saved, repo.update(valid))
        assertEquals(valid, repo.settings.first())
    }

    @Test
    fun `TC-S-092 settings repository rejects out-of-range values and keeps the old one`() = runTest {
        val repo = create()
        repo.update(valid)
        val badCooldown = valid.copy(cooldownSeconds = SettingsLimits.COOLDOWN_SECONDS.last + 1)
        assertEquals(SettingsResult.Invalid(setOf(SettingsError.COOLDOWN_OUT_OF_RANGE)), repo.update(badCooldown))
        val badOffset = valid.copy(offsetBpm = SettingsLimits.OFFSET_BPM.last + 1)
        assertEquals(SettingsResult.Invalid(setOf(SettingsError.OFFSET_OUT_OF_RANGE)), repo.update(badOffset))
        val badCadence = valid.copy(cadenceMinutes = 0)
        assertEquals(SettingsResult.Invalid(setOf(SettingsError.CADENCE_OUT_OF_RANGE)), repo.update(badCadence))
        val newSnooze = valid.copy(snooze = SnoozeSettings(minutes = 5, maxCount = 2))
        assertEquals(SettingsResult.Saved, repo.update(newSnooze))
        val several = valid.copy(cooldownSeconds = -1, cadenceMinutes = -1)
        assertEquals(
            SettingsResult.Invalid(setOf(SettingsError.COOLDOWN_OUT_OF_RANGE, SettingsError.CADENCE_OUT_OF_RANGE)),
            repo.update(several)
        )
        assertEquals(newSnooze, repo.settings.first())
    }
}

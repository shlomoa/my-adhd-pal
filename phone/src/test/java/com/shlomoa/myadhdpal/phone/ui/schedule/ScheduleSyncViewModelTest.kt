package com.shlomoa.myadhdpal.phone.ui.schedule

import com.shlomoa.myadhdpal.phone.data.PublishOutcome
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private class ScriptedSchedule(first: Long? = null) : ScheduleRepository {
    val version = MutableStateFlow(first)
    var next: suspend () -> PublishOutcome = { PublishOutcome.Unchanged }
    override val lastPublishedVersion: Flow<Long?> = version

    override suspend fun publish(): PublishOutcome = next().also {
        if (it is PublishOutcome.Published) version.value = it.version
    }
}

/** Step 4.2: sending the schedule to the watch from the Tasks tab, and saying what happened. */
class ScheduleSyncViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val schedule = ScriptedSchedule()
    private val vm by lazy { ScheduleSyncViewModel(schedule) }

    @Test
    fun `TC-A-122 before anything is sent nothing is claimed, and a version sent earlier is shown`() = runTest {
        assertEquals(ScheduleSyncUiState(null, SyncStatus.Idle), vm.uiState.value)
        schedule.version.value = 4
        assertEquals(ScheduleSyncUiState(4, SyncStatus.Idle), vm.uiState.value)
    }

    @Test
    fun `TC-A-123 a sent schedule is reported with its version`() = runTest {
        schedule.next = { PublishOutcome.Published(3) }
        vm.send()
        assertEquals(ScheduleSyncUiState(3, SyncStatus.Sent(3)), vm.uiState.value)
    }

    @Test
    fun `TC-A-124 an unchanged schedule says the watch is up to date`() = runTest {
        schedule.version.value = 2
        schedule.next = { PublishOutcome.Unchanged }
        vm.send()
        assertEquals(ScheduleSyncUiState(2, SyncStatus.UpToDate), vm.uiState.value)
    }

    @Test
    fun `TC-A-125 a failed send shows the reason, keeps the old version, and can be tried again`() = runTest {
        schedule.version.value = 2
        schedule.next = { PublishOutcome.Failed("watch not reachable") }
        vm.send()
        assertEquals(ScheduleSyncUiState(2, SyncStatus.Failed("watch not reachable")), vm.uiState.value)
        schedule.next = { PublishOutcome.Published(3) }
        vm.send()
        assertEquals(ScheduleSyncUiState(3, SyncStatus.Sent(3)), vm.uiState.value)
    }

    @Test
    fun `TC-A-126 while sending the state says so, and a second tap does not send twice`() = runTest {
        val gate = CompletableDeferred<PublishOutcome>()
        var calls = 0
        schedule.next = {
            calls++
            gate.await()
        }
        vm.send()
        vm.send()
        assertEquals(SyncStatus.Sending, vm.uiState.value.status)
        gate.complete(PublishOutcome.Published(1))
        assertEquals(SyncStatus.Sent(1), vm.uiState.value.status)
        assertEquals(1, calls)
    }
}

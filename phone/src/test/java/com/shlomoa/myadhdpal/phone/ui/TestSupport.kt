package com.shlomoa.myadhdpal.phone.ui

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Wednesday 2026-10-14 12:00 UTC. ISO day of week 3. */
val NOW: Instant = Instant.parse("2026-10-14T12:00:00Z")
val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

/** Runs ViewModels' `viewModelScope` eagerly so `uiState.value` is current after each call. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** A counter-based id source so tests know the ids. */
class SequentialIds(private val prefix: String = "id") {
    private var n = 0

    fun next(): String = "$prefix-${++n}"
}

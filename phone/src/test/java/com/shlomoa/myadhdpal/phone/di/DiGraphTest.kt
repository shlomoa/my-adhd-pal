package com.shlomoa.myadhdpal.phone.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.shlomoa.myadhdpal.phone.MainActivity
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val FIXED: Instant = Instant.parse("2026-01-02T03:04:05Z")

@HiltViewModel
class ProbeViewModel @Inject constructor(val clock: Clock) : ViewModel()

/** TC-A-010: a ViewModel built by the DI graph receives a fake in place of the production binding. */
@HiltAndroidTest
@UninstallModules(ClockModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class FakeBindingTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    // Nested so the replacement applies to this test only.
    @Module
    @InstallIn(SingletonComponent::class)
    object FakeClockModule {
        @Provides
        fun clock(): Clock = Clock.fixed(FIXED, ZoneOffset.UTC)
    }

    @Test
    fun `TC-A-010 ViewModel resolves through the DI graph with a fake clock`() {
        hiltRule.inject()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[ProbeViewModel::class.java]
                assertEquals(FIXED, vm.clock.instant())
            }
        }
    }
}

/** TC-A-012: with no test replacement the production graph supplies the real system clock. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ProductionBindingTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var clock: Clock

    @Test
    fun `TC-A-012 production graph binds the system clock`() {
        hiltRule.inject()
        val now = System.currentTimeMillis()
        assertTrue(kotlin.math.abs(clock.millis() - now) < 60_000)
        assertEquals(Clock.systemDefaultZone().zone, clock.zone)
    }
}

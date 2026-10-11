package com.shlomoa.myadhdpal.phone.compat

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

class Greeter @Inject constructor() {
    fun greet() = "hello"
}

/** Toolchain compatibility sample (step 0b.1): Hilt + KSP on AGP 9.4 built-in Kotlin and Kotlin 2.4. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class InjectionSampleTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var greeter: Greeter

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `TC-S-120 Hilt generates the component and injects a class`() {
        assertEquals("hello", greeter.greet())
    }
}

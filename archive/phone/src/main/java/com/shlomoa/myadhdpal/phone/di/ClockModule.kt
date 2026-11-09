package com.shlomoa.myadhdpal.phone.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** The one place the app obtains time, so tests can inject a fixed clock. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}

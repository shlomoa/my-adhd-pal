package com.shlomoa.myadhdpal.phone.debug

import android.util.Log
import com.shlomoa.myadhdpal.phone.alert.AlertObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Clock
import javax.inject.Singleton

/** Debug builds only: the demo trace watches the alert raiser. */
@Module
@InstallIn(SingletonComponent::class)
object DemoAlertModule {
    @Provides
    @Singleton
    fun demoAlertTrace(clock: Clock): DemoAlertTrace = DemoAlertTrace(clock) { Log.i(DemoActions.LOG_TAG, it) }

    @Provides
    @IntoSet
    fun demoAlertObserver(trace: DemoAlertTrace): AlertObserver = trace
}

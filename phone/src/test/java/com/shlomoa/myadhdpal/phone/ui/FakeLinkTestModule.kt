package com.shlomoa.myadhdpal.phone.ui

import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.di.LinkModule
import com.shlomoa.myadhdpal.phone.link.DataLayerClient
import com.shlomoa.myadhdpal.phone.link.DataLayerWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.link.FakeDataLayerClient
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Clock
import javax.inject.Singleton

/** For every Hilt test: the real graph (Room, DataStore, repositories) with only the Play services client faked. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LinkModule::class])
object FakeLinkTestModule {
    @Provides
    @Singleton
    fun client(): DataLayerClient = FakeDataLayerClient()

    @Provides
    @Singleton
    fun source(client: DataLayerClient, clock: Clock): DataLayerWatchLinkDataSource =
        DataLayerWatchLinkDataSource(client, clock)

    @Provides
    fun watchLink(source: DataLayerWatchLinkDataSource): WatchLinkDataSource = source
}

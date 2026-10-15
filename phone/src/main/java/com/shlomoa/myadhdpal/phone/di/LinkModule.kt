package com.shlomoa.myadhdpal.phone.di

import android.content.Context
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.link.DataLayerClient
import com.shlomoa.myadhdpal.phone.link.DataLayerWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.link.GmsDataLayerClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** The real link to the watch: one Data Layer client and one data source for the whole process. */
@Module
@InstallIn(SingletonComponent::class)
object LinkModule {
    @Provides
    @Singleton
    fun dataLayerClient(@ApplicationContext context: Context): DataLayerClient = GmsDataLayerClient(context)

    @Provides
    @Singleton
    fun dataSource(client: DataLayerClient, clock: Clock): DataLayerWatchLinkDataSource =
        DataLayerWatchLinkDataSource(client, clock)

    @Provides
    fun watchLinkDataSource(source: DataLayerWatchLinkDataSource): WatchLinkDataSource = source
}

package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.DefaultWatchStatusRepository
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.WatchStatusRepository
import com.shlomoa.myadhdpal.phone.data.contract.WatchStatusRepositoryContract

class DefaultWatchStatusRepositoryTest : WatchStatusRepositoryContract() {
    override fun create(source: WatchLinkDataSource): WatchStatusRepository = DefaultWatchStatusRepository(source)
}

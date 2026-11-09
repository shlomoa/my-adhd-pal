package com.shlomoa.myadhdpal.phone.data.contract

import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.WatchStatusRepository
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.protocol.HeartRatePermission
import com.shlomoa.myadhdpal.protocol.WatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every [WatchStatusRepository] must pass these. */
abstract class WatchStatusRepositoryContract {
    abstract fun create(source: WatchLinkDataSource): WatchStatusRepository

    @Test
    fun `TC-S-165 watch status repository is null until the watch reports and then follows the source`() = runTest {
        val source = FakeWatchLinkDataSource()
        val repo = create(source)
        assertNull(repo.status.first())
        val granted =
            WatchStatus(baselineBpm = 58, thresholdBpm = 88, heartRatePermission = HeartRatePermission.GRANTED)
        source.setWatchStatus(granted)
        assertEquals(granted, repo.status.first())
        val denied = WatchStatus(heartRatePermission = HeartRatePermission.DENIED)
        source.setWatchStatus(denied)
        assertEquals(denied, repo.status.first())
    }
}

package com.shlomoa.myadhdpal.phone.data.contract

import com.shlomoa.myadhdpal.phone.data.LinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.protocol.LinkState
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every [LinkStatusRepository] must pass these. */
abstract class LinkStatusRepositoryContract {
    abstract fun create(source: WatchLinkDataSource): LinkStatusRepository

    @Test
    fun `TC-S-092 link status repository reflects the data source state and last sync`() = runTest {
        val source = FakeWatchLinkDataSource(now = { Instant.parse("2026-10-14T12:00:00Z") })
        val repo = create(source)
        assertEquals(LinkState.AWAY, repo.linkState.first())
        assertEquals(null, repo.lastSync.first())
        source.connect(nearby = false)
        assertEquals(LinkState.RELAYED, repo.linkState.first())
        source.setLinkState(LinkState.NO_WATCH_APP)
        assertEquals(LinkState.NO_WATCH_APP, repo.linkState.first())
    }
}

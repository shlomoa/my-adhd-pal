package com.shlomoa.myadhdpal.phone.ui.link

import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryLinkStatusRepository
import com.shlomoa.myadhdpal.phone.ui.FIXED_CLOCK
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.Side
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Step 2.6. The screen shows the state and the last sync; it has no controls. */
class LinkStateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    // An item delivered while linked sets the last sync to this time: 90 seconds before "now".
    private val source = FakeWatchLinkDataSource { FIXED_CLOCK.instant().minusSeconds(90) }
    private val vm = LinkStateViewModel(InMemoryLinkStatusRepository(source), FIXED_CLOCK)

    @Test
    fun `TC-A-001 Nearby is shown`() {
        source.setLinkState(LinkState.NEARBY)
        assertEquals(LinkState.NEARBY, vm.uiState.value.state)
    }

    @Test
    fun `TC-A-002 Relayed is shown`() {
        source.setLinkState(LinkState.RELAYED)
        assertEquals(LinkState.RELAYED, vm.uiState.value.state)
    }

    @Test
    fun `TC-A-003 Away is shown`() {
        source.setLinkState(LinkState.AWAY)
        assertEquals(LinkState.AWAY, vm.uiState.value.state)
    }

    @Test
    fun `TC-A-004 No watch app is shown`() {
        source.setLinkState(LinkState.NO_WATCH_APP)
        assertEquals(LinkState.NO_WATCH_APP, vm.uiState.value.state)
    }

    @Test
    fun `TC-A-005 Incompatible is shown`() {
        source.setLinkState(LinkState.INCOMPATIBLE)
        assertEquals(LinkState.INCOMPATIBLE, vm.uiState.value.state)
    }

    @Test
    fun `TC-A-006 last sync is an age from the clock, or Never when absent`() {
        assertEquals(LastSync.Never, vm.uiState.value.lastSync)
        source.connect(nearby = true)
        source.emit(WatchEvent.HelloReceived("h-1", 1L, Hello(Side.WATCH, "1.1", "1.0")))
        assertEquals(LastSync.Ago(Duration.ofSeconds(90)), vm.uiState.value.lastSync)
    }

    @Test
    fun `TC-A-007 the screen has no controls`() {
        // The only public member is the state; no actions to call.
        val actions = LinkStateViewModel::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .map { it.name }
        assertEquals(listOf("getUiState"), actions)
    }

    @Test
    fun `TC-A-008 the state follows the link`() {
        source.connect(nearby = true)
        assertEquals(LinkState.NEARBY, vm.uiState.value.state)
        source.disconnect()
        assertEquals(LinkState.AWAY, vm.uiState.value.state)
        assertTrue(vm.uiState.value.lastSync == LastSync.Never)
    }
}

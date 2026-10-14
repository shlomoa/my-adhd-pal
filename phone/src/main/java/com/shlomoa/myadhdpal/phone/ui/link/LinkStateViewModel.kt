package com.shlomoa.myadhdpal.phone.ui.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shlomoa.myadhdpal.phone.data.LinkStatusRepository
import com.shlomoa.myadhdpal.protocol.LinkState
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** When the watch last sent something: never, or how long ago by the injected clock. */
sealed interface LastSync {
    data object Never : LastSync

    data class Ago(val age: Duration) : LastSync
}

data class LinkStateUiState(val state: LinkState, val lastSync: LastSync)

/**
 * Step 2.6: the link state and the last sync, read-only (no controls). It becomes a `@HiltViewModel` when the real
 * [LinkStatusRepository] is bound (data layer, step 2.5); until then there is no production binding to inject.
 */
class LinkStateViewModel @Inject constructor(linkStatus: LinkStatusRepository, clock: Clock) : ViewModel() {
    val uiState: StateFlow<LinkStateUiState> = combine(linkStatus.linkState, linkStatus.lastSync) { state, sync ->
        val lastSync = sync?.let { LastSync.Ago(Duration.between(it, clock.instant()).coerceAtLeast(Duration.ZERO)) }
        LinkStateUiState(state, lastSync ?: LastSync.Never)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LinkStateUiState(LinkState.AWAY, LastSync.Never))
}

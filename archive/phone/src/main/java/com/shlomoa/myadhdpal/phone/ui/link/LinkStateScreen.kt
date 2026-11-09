package com.shlomoa.myadhdpal.phone.ui.link

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.R
import com.shlomoa.myadhdpal.protocol.LinkState
import java.time.Duration

@StringRes
private fun LinkState.label(): Int = when (this) {
    LinkState.NEARBY -> R.string.link_nearby
    LinkState.RELAYED -> R.string.link_relayed
    LinkState.AWAY -> R.string.link_away
    LinkState.NO_WATCH_APP -> R.string.link_no_watch_app
    LinkState.INCOMPATIBLE -> R.string.link_incompatible
}

/** Stateless: shows the state and the last sync, nothing to press. */
@Composable
fun LinkStateScreen(state: LinkStateUiState, modifier: Modifier = Modifier) {
    val label = stringResource(state.state.label())
    val sync = when (val s = state.lastSync) {
        LastSync.Never -> stringResource(R.string.link_last_sync_never)
        is LastSync.Ago -> stringResource(R.string.link_last_sync_ago, s.age.toMinutes())
    }
    Column(modifier.padding(16.dp).semantics(mergeDescendants = true) { contentDescription = "$label. $sync" }) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
        Text(sync, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview
@Composable
private fun LinkStateScreenPreview() {
    MaterialTheme { LinkStateScreen(LinkStateUiState(LinkState.NEARBY, LastSync.Ago(Duration.ofMinutes(2)))) }
}

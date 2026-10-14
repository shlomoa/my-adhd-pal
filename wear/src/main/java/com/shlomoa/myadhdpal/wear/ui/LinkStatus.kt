package com.shlomoa.myadhdpal.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.wear.R
import com.shlomoa.myadhdpal.wear.link.LinkView
import com.shlomoa.myadhdpal.wear.link.watchLabelRes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The link state and last sync time, for the home screen. */
@Composable
fun LinkStatus(view: LinkView) {
    Text(
        text = stringResource(view.state.watchLabelRes()),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    val lastSync = view.lastSyncAt
    Text(
        text = if (lastSync == null) {
            stringResource(R.string.never_synced)
        } else {
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
            stringResource(R.string.last_sync, time.format(lastSync))
        },
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center
    )
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun LinkStatusPreview() {
    LinkStatus(LinkView(LinkState.RELAYED, null))
}

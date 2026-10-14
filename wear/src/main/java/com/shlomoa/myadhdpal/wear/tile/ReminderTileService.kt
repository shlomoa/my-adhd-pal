package com.shlomoa.myadhdpal.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import com.shlomoa.myadhdpal.wear.R
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.link.watchLabelRes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The glanceable tile (step 5.2): the next reminder with its time, a button that starts the two minutes for it, and the
 * link state with the last sync. What is shown comes from [tileModel]; this class only draws it.
 */
class ReminderTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val graph = (application as WatchApplication).graph
        return tile(timeline = Timeline.fromLayoutElement(layout(graph.tileModel())))
    }

    private fun MaterialScope.layout(model: TileModel): LayoutElement {
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
        val reminder = model.reminder
        val lastSync = model.link.lastSyncAt
        val syncText =
            if (lastSync ==
                null
            ) {
                getString(R.string.never_synced)
            } else {
                getString(R.string.last_sync, time.format(lastSync))
            }
        return primaryLayout(
            titleSlot = { text(reminder?.let { time.format(it.at) }.orEmpty().layoutString) },
            mainSlot = {
                text((reminder?.title ?: getString(R.string.tile_no_reminders)).layoutString)
            },
            bottomSlot = model.action?.let { action -> { startButton(action) } },
            labelForBottomSlot = { text("${getString(model.link.state.watchLabelRes())}, $syncText".layoutString) }
        )
    }

    private fun MaterialScope.startButton(action: TileAction): LayoutElement {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(TileActionActivity::class.java.name)
        action.toExtras().forEach { (key, value) ->
            activity.addKeyToExtraMapping(key, ActionBuilders.stringExtra(value))
        }
        val launch = ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build()
        return textEdgeButton(onClick = clickable(launch)) { text(getString(R.string.action_start_2_min).layoutString) }
    }
}

package com.shlomoa.myadhdpal.wear.tile

import com.shlomoa.myadhdpal.wear.link.LinkView

/**
 * The events that change what the tile shows: a schedule was applied, an action was handled and the link state or last
 * sync changed. Each asks for a new tile ([refresh] is `TileService.getUpdater(...).requestUpdate(...)` in the app).
 */
class TileUpdateTriggers(private val refresh: () -> Unit) {
    fun onScheduleApplied() = refresh()

    fun onActionHandled() = refresh()

    /** The new view is not needed: any change of the link state or the last sync redraws the tile. */
    @Suppress("UNUSED_PARAMETER")
    fun onLinkChanged(view: LinkView) = refresh()
}

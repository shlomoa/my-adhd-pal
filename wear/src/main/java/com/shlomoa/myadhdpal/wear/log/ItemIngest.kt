package com.shlomoa.myadhdpal.wear.log

/**
 * Entry point for an item that arrived on the Data Layer, identified by its path and JSON body. Production listeners
 * and the debug-only injected-message receiver both call [ItemIngest.handler], so injected items take the same path as
 * real ones. The real handler is installed with the schedule receiver (3.3).
 */
fun interface ItemHandler {
    fun onItem(path: String, body: String)
}

object ItemIngest {
    @Volatile
    var handler: ItemHandler =
        ItemHandler { path, _ -> Logger.w("ItemIngest", "no handler installed, dropped item for $path") }
}

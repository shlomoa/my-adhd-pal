package com.shlomoa.myadhdpal.wear.debug

import com.shlomoa.myadhdpal.wear.log.ItemHandler
import com.shlomoa.myadhdpal.wear.log.Logger

private const val TAG = "InjectedMessage"

/** Hands an item injected with `adb` to the real ingest path; items without a path or body are dropped and logged. */
fun forwardInjected(path: String?, body: String?, handler: ItemHandler) {
    if (path.isNullOrBlank() || body == null) {
        Logger.w(TAG, "dropped: extras 'path' and 'body' are both required")
        return
    }
    Logger.d(TAG, "injected item for $path (${body.length} chars)")
    handler.onItem(path, body)
}

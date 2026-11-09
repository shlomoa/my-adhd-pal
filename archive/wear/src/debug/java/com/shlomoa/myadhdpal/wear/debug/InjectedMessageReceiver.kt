package com.shlomoa.myadhdpal.wear.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shlomoa.myadhdpal.wear.log.ItemIngest

/**
 * Debug-only. Simulates a Data Layer item arriving, for example:
 * `adb shell am broadcast -n com.shlomoa.myadhdpal/.wear.debug.InjectedMessageReceiver`
 * followed by `--es path /schedule --es body '{...}'`.
 */
class InjectedMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        forwardInjected(intent.getStringExtra(EXTRA_PATH), intent.getStringExtra(EXTRA_BODY), ItemIngest.handler)
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_BODY = "body"
    }
}

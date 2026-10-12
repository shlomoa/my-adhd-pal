package com.shlomoa.myadhdpal.wear.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * THROWAWAY spike code. Lets a script drive the spike without tapping:
 * adb shell am broadcast -n <package>/<this class> -a <Spike.ACTION_*> --es kind del --ez urgent true
 */
class SpikeCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val done = { pending.finish() }
        val urgent = intent.getBooleanExtra("urgent", true)
        Spike.log(context, "COMMAND action=${intent.action} kind=${intent.getStringExtra("kind")} urgent=$urgent")
        when (intent.action) {
            Spike.ACTION_SEND -> Spike.send(context, intent.getStringExtra("kind") ?: "del", urgent, done)
            Spike.ACTION_PING -> Spike.ping(context, urgent, done)
            Spike.ACTION_LIST_ITEMS -> Spike.listItems(context, done)
            Spike.ACTION_LIST_NODES -> Spike.listNodes(context, done)
            Spike.ACTION_CLEAR_LOG -> {
                Spike.clearLog(context)
                done()
            }
            else -> {
                Spike.log(context, "COMMAND-UNKNOWN action=${intent.action}")
                done()
            }
        }
    }
}

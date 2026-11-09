package com.shlomoa.myadhdpal.poc.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager

/**
 * Invisible entry point for test commands. A health foreground service may only start while the app has a
 * visible activity, so every command goes through here and is forwarded to [PocService] with its extras.
 *
 * With the extra `keep_screen=true` the activity stays on screen and holds the screen on (an "screen on" phase);
 * any later command without it finishes the activity.
 */
class ControlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(command: Intent) {
        val forward = Intent(this, PocService::class.java)
        command.extras?.let { forward.putExtras(it) }
        startForegroundService(forward)
        if (command.getBooleanExtra("keep_screen", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            finish()
        }
    }
}

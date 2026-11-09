package com.shlomoa.myadhdpal.poc.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible entry point for test commands. A health foreground service may only be started while the app has a
 * visible activity, so every command goes through here and is forwarded to [PocService] with its extras.
 */
class ControlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forward = Intent(this, PocService::class.java)
        intent.extras?.let { forward.putExtras(it) }
        startForegroundService(forward)
        finish()
    }
}

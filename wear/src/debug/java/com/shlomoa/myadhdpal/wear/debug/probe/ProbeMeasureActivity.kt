package com.shlomoa.myadhdpal.wear.debug.probe

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Debug only. Starts [ProbeMeasureService] from a visible activity, as the user would start the app:
 * `adb shell am start -n com.shlomoa.myadhdpal/.wear.debug.probe.ProbeMeasureActivity`. Needs only the foreground
 * heart-rate permission (the first of [requiredPermissions]); the background one is deliberately not required.
 */
class ProbeMeasureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val foreground = requiredPermissions(Build.VERSION.SDK_INT).first()
        if (checkSelfPermission(foreground) == PackageManager.PERMISSION_GRANTED) {
            startForegroundService(Intent(this, ProbeMeasureService::class.java))
        } else {
            ProbeLog.note("measure not started: permission missing: $foreground")
        }
        finish()
    }
}

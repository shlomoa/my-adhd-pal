package com.shlomoa.myadhdpal.wear.debug.probe

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Debug only. Shows the system permission prompt(s) on the watch when `adb shell pm grant` cannot grant them:
 * `adb shell am start -n com.shlomoa.myadhdpal/.wear.debug.probe.ProbePermissionActivity`. The foreground permission is
 * asked first and the background one after it, as Android requires. The result is logged as `PROBE permission ...`.
 */
class ProbePermissionActivity : ComponentActivity() {
    private val required = requiredPermissions(Build.VERSION.SDK_INT)
    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { next() }
    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        next()
    }

    private fun next() {
        val remaining = if (step == 0) required.take(1) else required.drop(1)
        step++
        val pending = remaining.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        when {
            pending.isNotEmpty() -> ask.launch(pending.toTypedArray())
            step < 2 -> next()
            else -> finishWithReport()
        }
    }

    private fun finishWithReport() {
        permissionReport(required) { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            .forEach(ProbeLog::note)
        finish()
    }
}

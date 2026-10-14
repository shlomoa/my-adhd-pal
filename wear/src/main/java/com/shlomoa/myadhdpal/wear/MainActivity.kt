package com.shlomoa.myadhdpal.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.shlomoa.myadhdpal.wear.scheduler.SchedulerStatus
import com.shlomoa.myadhdpal.wear.ui.MyAdhdPalApp

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val status = (application as WatchApplication).graph.scheduler.rearm()
        val inexact = status is SchedulerStatus.Armed && !status.exact
        setContent { MyAdhdPalApp(inexactAlarms = inexact) }
    }

    /** Reminders are notifications; from Wear OS 4 (API 33) the user has to allow them. */
    private fun requestNotificationPermissionIfNeeded() {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needed) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

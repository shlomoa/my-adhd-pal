package com.shlomoa.myadhdpal.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
        keepLinkStateFresh()
        setContent { MyAdhdPalApp(inexactAlarms = inexact) }
    }

    /** The Data Layer has no callback for connect or disconnect; re-read the nodes about every 2 s while shown. */
    private fun keepLinkStateFresh() {
        val graph = (application as WatchApplication).graph
        val handler = Handler(Looper.getMainLooper())
        val refresh = object : Runnable {
            override fun run() {
                graph.dataLayer().refreshLinkState()
                handler.postDelayed(this, LINK_REFRESH_MS)
            }
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = refresh.run()

            override fun onStop(owner: LifecycleOwner) = handler.removeCallbacks(refresh)
        })
    }

    private companion object {
        const val LINK_REFRESH_MS = 2_000L
    }

    /** Reminders are notifications; from Wear OS 4 (API 33) the user has to allow them. */
    private fun requestNotificationPermissionIfNeeded() {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needed) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

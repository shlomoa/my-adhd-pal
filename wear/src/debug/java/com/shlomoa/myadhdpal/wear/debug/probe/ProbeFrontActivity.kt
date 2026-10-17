package com.shlomoa.myadhdpal.wear.debug.probe

import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ToggleButton
import androidx.activity.ComponentActivity

/**
 * Debug only (experiment E4, issue #231): a screen that stays in front, with a switch for keep-screen-on. It writes a
 * `PROBE front` line when it is resumed or paused, gains or loses focus, the switch changes or the screen state
 * changes (on, doze, off). Started like the app would be:
 * `adb shell am start -n com.shlomoa.myadhdpal/.wear.debug.probe.ProbeFrontActivity [--ez keep true]`.
 * It does not measure anything.
 */
class ProbeFrontActivity : ComponentActivity() {
    private var resumed = false
    private var hasFocus = false
    private lateinit var displays: DisplayManager

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = note("display-changed")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displays = getSystemService(DisplayManager::class.java)
        val keep = ToggleButton(this).apply {
            textOn = "Keep screen on: ON"
            textOff = "Keep screen on: off"
            isChecked = intent.getBooleanExtra(EXTRA_KEEP, false)
            setOnCheckedChangeListener { _, checked ->
                setKeepScreenOn(checked)
                note("keep-toggled")
            }
        }
        setKeepScreenOn(keep.isChecked)
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(keep)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        displays.registerDisplayListener(listener, null)
        note("resumed")
    }

    override fun onPause() {
        resumed = false
        displays.unregisterDisplayListener(listener)
        note("paused")
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        this.hasFocus = hasFocus
        note("focus")
    }

    private fun setKeepScreenOn(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun note(event: String) {
        val keepOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        ProbeLog.note(frontLine(event, display.state, isInFront(resumed, hasFocus), keepOn))
    }

    companion object {
        const val EXTRA_KEEP = "keep"
    }
}

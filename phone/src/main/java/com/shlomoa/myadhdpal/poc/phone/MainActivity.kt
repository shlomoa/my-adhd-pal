package com.shlomoa.myadhdpal.poc.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.shlomoa.myadhdpal.poc.core.LiveFormat
import com.shlomoa.myadhdpal.poc.core.LiveStatus

/**
 * The live pulse indicator: a status light (green live, amber stale, red no signal), the pulse in beats per minute,
 * and everything else the watch reports or that follows from it: signal accuracy, age of the last reading, latency on
 * each path, minimum / average / maximum over the last minute, the trend, and the mean beat interval with an honest
 * label that it is an average and not a true beat-to-beat interval.
 */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var light: View
    private lateinit var status: TextView
    private lateinit var pulse: TextView
    private lateinit var details: TextView
    private lateinit var link: TextView

    private val refresh = object : Runnable {
        override fun run() {
            val snapshot = PhoneState.live.snapshot(SystemClock.elapsedRealtimeNanos())
            val color = when (snapshot.status) {
                LiveStatus.LIVE -> GREEN
                LiveStatus.STALE -> AMBER
                LiveStatus.NO_SIGNAL -> RED
            }
            (light.background as GradientDrawable).setColor(color)
            status.text = LiveFormat.statusLabel(snapshot)
            status.setTextColor(color)
            pulse.text = LiveFormat.headline(snapshot)
            details.text = LiveFormat.details(snapshot).joinToString("\n")
            link.text = "${PhoneState.bluetoothText}\nreceived ${PhoneState.received} " +
                "(Bluetooth ${PhoneState.receivedBt}, Data Layer ${PhoneState.receivedDl})"
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PAD, PAD, PAD, PAD)
        }
        light = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(LIGHT_SIZE, LIGHT_SIZE).apply { rightMargin = PAD / 2 }
        }
        status = TextView(this).apply {
            textSize = STATUS_SIZE
            typeface = Typeface.DEFAULT_BOLD
        }
        column.addView(
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(light)
                addView(status)
            }
        )
        pulse = TextView(this).apply {
            textSize = PULSE_SIZE
            typeface = Typeface.DEFAULT_BOLD
        }
        details = TextView(this).apply { textSize = DETAIL_SIZE }
        link = TextView(this).apply {
            textSize = DETAIL_SIZE
            setPadding(0, PAD, 0, 0)
        }
        column.addView(pulse)
        column.addView(details)
        column.addView(link)
        setContentView(column)
        startForegroundService(Intent(this, BtServerService::class.java))
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private companion object {
        const val REFRESH_MS = 250L
        const val PAD = 48
        const val LIGHT_SIZE = 48
        const val STATUS_SIZE = 20f
        const val PULSE_SIZE = 72f
        const val DETAIL_SIZE = 15f
        val GREEN = Color.rgb(46, 125, 50)
        val AMBER = Color.rgb(249, 168, 37)
        val RED = Color.rgb(198, 40, 40)
    }
}

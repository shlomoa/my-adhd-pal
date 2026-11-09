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
    private lateinit var graph: PulseGraphView
    private lateinit var details: TextView
    private lateinit var beatNote: TextView

    @Volatile
    private var beatPeriodMs = 0L
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
            graph.update(PhoneState.live.graphPoints(SystemClock.elapsedRealtimeNanos()), snapshot.windowSeconds, color)
            val reported = snapshot.bpm
            beatPeriodMs =
                if (snapshot.status == LiveStatus.LIVE && reported != null) (MS_PER_MINUTE / reported).toLong() else 0L
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
        graph = PulseGraphView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, GRAPH_HEIGHT)
        }
        beatNote = TextView(this).apply {
            textSize = NOTE_SIZE
            text = "The light beats at the reported pulse rate; it is not synchronised with your actual heartbeats."
        }
        details = TextView(this).apply { textSize = DETAIL_SIZE }
        link = TextView(this).apply {
            textSize = DETAIL_SIZE
            setPadding(0, PAD, 0, 0)
        }
        column.addView(pulse)
        column.addView(graph)
        column.addView(beatNote)
        column.addView(details)
        column.addView(link)
        setContentView(column)
        startForegroundService(Intent(this, BtServerService::class.java))
    }

    /** Makes the status light swell once per beat at the reported rate while the pulse is live. */
    private val beat = object : Runnable {
        override fun run() {
            val period = beatPeriodMs
            if (period > 0) {
                light.animate().scaleX(BEAT_SCALE).scaleY(BEAT_SCALE).setDuration(BEAT_UP_MS).withEndAction {
                    light.animate().scaleX(1f).scaleY(1f).setDuration(BEAT_DOWN_MS).start()
                }.start()
            }
            handler.postDelayed(this, if (period > 0) period else IDLE_BEAT_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
        handler.post(beat)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(beat)
        super.onPause()
    }

    private companion object {
        const val REFRESH_MS = 250L
        const val MS_PER_MINUTE = 60_000.0
        const val GRAPH_HEIGHT = 520
        const val NOTE_SIZE = 11f
        const val BEAT_SCALE = 1.35f
        const val BEAT_UP_MS = 100L
        const val BEAT_DOWN_MS = 150L
        const val IDLE_BEAT_MS = 500L
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

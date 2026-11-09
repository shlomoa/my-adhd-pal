package com.shlomoa.myadhdpal.poc.phone

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView

/** Shows the latest live pulse value received from the watch. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var text: TextView

    private val refresh = object : Runnable {
        override fun run() {
            text.text = "${PhoneState.lastText}\nreceived: ${PhoneState.received}"
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            textSize = TEXT_SIZE
            setPadding(PAD, PAD, PAD, PAD)
        }
        setContentView(text)
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
        const val TEXT_SIZE = 28f
        const val PAD = 48
    }
}

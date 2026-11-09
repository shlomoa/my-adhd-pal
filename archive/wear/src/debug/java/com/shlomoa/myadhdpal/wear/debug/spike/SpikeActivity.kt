package com.shlomoa.myadhdpal.wear.debug.spike

import android.app.Activity
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Spike UI: plain views, no Compose. */
class SpikeActivity : Activity() {
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this).apply { textSize = 9f }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        fun button(label: String, action: () -> Unit) {
            column.addView(
                Button(this).apply {
                    text = label
                    setOnClickListener {
                        action()
                        refresh()
                    }
                }
            )
        }
        button("Send DEL urgent") { Spike.send(this, "del", urgent = true) }
        button("Send DEL non-urgent") { Spike.send(this, "del", urgent = false) }
        button("Send KEEP urgent") { Spike.send(this, "keep", urgent = true) }
        button("List data items") { Spike.listItems(this) }
        button("List connected nodes") { Spike.listNodes(this) }
        button("Refresh log") { }
        button("Clear log") { Spike.clearLog(this) }
        column.addView(logView)
        setContentView(
            ScrollView(this).apply {
                addView(column)
                // Edge-to-edge on targetSdk 36 plus a round screen: inset for the system bars, and on a round display
                // add side and top/bottom padding so the first and last buttons can be scrolled into the visible area.
                val round = resources.configuration.isScreenRound
                val extraV = if (round) (resources.displayMetrics.heightPixels * 0.18f).toInt() else 0
                val extraH = if (round) (resources.displayMetrics.widthPixels * 0.10f).toInt() else 0
                setOnApplyWindowInsetsListener { view, insets ->
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left + extraH, bars.top + extraV, bars.right + extraH, bars.bottom + extraV)
                    insets
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        Spike.foreground = true
        Spike.log(this, "ACTIVITY-RESUMED")
        refresh()
    }

    override fun onPause() {
        Spike.foreground = false
        super.onPause()
    }

    private fun refresh() {
        logView.postDelayed({ logView.text = Spike.readLog(this).lines().takeLast(25).joinToString("\n") }, 500)
    }
}

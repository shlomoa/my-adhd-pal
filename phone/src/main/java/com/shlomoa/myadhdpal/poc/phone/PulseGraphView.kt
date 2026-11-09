package com.shlomoa.myadhdpal.poc.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.shlomoa.myadhdpal.poc.core.GraphPoint
import com.shlomoa.myadhdpal.poc.core.PulseGraph

/** The pulse over the last minute as a line graph: bpm grid at the left, time left to right, newest at the right. */
class PulseGraphView(context: Context) : View(context) {
    private var points: List<GraphPoint> = emptyList()
    private var windowSeconds = DEFAULT_WINDOW
    private var lineColor = Color.GRAY
    private val density = resources.displayMetrics.density

    private val grid = Paint().apply {
        color = Color.argb(60, 128, 128, 128)
        strokeWidth = density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = LABEL_SP * density
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = LINE_DP * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun update(newPoints: List<GraphPoint>, window: Double, color: Int) {
        points = newPoints
        windowSeconds = window
        lineColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val left = LEFT_MARGIN_DP * density
        val bottom = height - BOTTOM_MARGIN_DP * density
        val plotWidth = width - left
        val plotHeight = bottom
        val layout = PulseGraph.layout(points, windowSeconds, plotWidth, plotHeight)
        for (tick in layout.ticks) {
            val y = plotHeight * (1f - (tick - layout.lowBpm) / (layout.highBpm - layout.lowBpm).toFloat())
            canvas.drawLine(left, y, width.toFloat(), y, grid)
            canvas.drawText(tick.toString(), 0f, y + label.textSize / LABEL_CENTER, label)
        }
        canvas.drawText("-${windowSeconds.toInt()} s", left, height.toFloat() - density, label)
        canvas.drawText("now", width - label.measureText("now"), height.toFloat() - density, label)
        line.color = lineColor
        dot.color = lineColor
        for (segment in layout.segments) {
            val path = Path()
            segment.forEachIndexed { i, p ->
                if (i ==
                    0
                ) {
                    path.moveTo(left + p.x, p.y)
                } else {
                    path.lineTo(left + p.x, p.y)
                }
            }
            canvas.drawPath(path, line)
        }
        layout.segments.lastOrNull()?.lastOrNull()?.let { canvas.drawCircle(left + it.x, it.y, DOT_DP * density, dot) }
        if (layout.segments.isEmpty()) {
            canvas.drawText("waiting for pulse readings...", left + density * WAIT_INDENT, plotHeight / 2, label)
        }
    }

    private companion object {
        const val DEFAULT_WINDOW = 60.0
        const val LEFT_MARGIN_DP = 32f
        const val BOTTOM_MARGIN_DP = 18f
        const val LABEL_SP = 11f
        const val LINE_DP = 2.5f
        const val DOT_DP = 4f
        const val LABEL_CENTER = 3f
        const val WAIT_INDENT = 8f
    }
}

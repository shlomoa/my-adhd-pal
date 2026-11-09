package com.shlomoa.myadhdpal.poc.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.shlomoa.myadhdpal.poc.core.BeatDetector
import com.shlomoa.myadhdpal.poc.core.Hrv

/**
 * Probe for the question "can PPG, beat-to-beat intervals (IBI) and heart-rate variability (HRV) be obtained?".
 *
 * It lists the heart-related sensors the watch exposes to apps, registers the raw heart sensor (on this watch the
 * system lists a `Samsung HR Raw Sensor`, type `com.samsung.sensor.hr_raw`, readable with the heart-rate permission),
 * records every event for the length of the run and, at the end, reports the event count, the real sample rate and, per
 * channel, the beats found and the HRV numbers computed from them. Nothing is assumed: if the sensor delivers nothing
 * to this app, the report says so.
 */
class RawProbe(private val context: Context, private val log: (String) -> Unit) {
    private val times = ArrayList<Long>()
    private val values = ArrayList<FloatArray>()
    private var sensorDescription = ""

    @Volatile
    var error: String = ""
        private set

    val count: Int get() = times.size

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (times.size >= MAX_EVENTS) return
            times += event.timestamp
            values += event.values.copyOf()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = log("raw sensor accuracy changed to $accuracy")
    }

    fun start(): Boolean {
        val manager = context.getSystemService(SensorManager::class.java)
        val all = manager.getSensorList(Sensor.TYPE_ALL)
        val heartRelated = all.filter { HEART_WORDS.containsMatchIn("${it.name} ${it.stringType}") }
        log("heart-related sensors visible to apps: " + heartRelated.joinToString(" | ") { describe(it) })
        val raw = all.firstOrNull { it.stringType == RAW_TYPE }
            ?: all.firstOrNull { RAW_NAME_WORDS.containsMatchIn(it.name) }
        if (raw == null) {
            error = "no raw heart sensor is visible to apps"
            log(error)
            return false
        }
        sensorDescription = describe(raw)
        return try {
            val ok = manager.registerListener(listener, raw, SensorManager.SENSOR_DELAY_FASTEST)
            log("raw sensor registered: $sensorDescription -> $ok")
            if (!ok) error = "registerListener returned false for the raw sensor"
            ok
        } catch (e: SecurityException) {
            error = "raw sensor refused: ${e.message}"
            log(error)
            false
        }
    }

    /** Stops listening, writes the raw samples to `watch-raw-<label>.csv` and returns the text for the summary. */
    fun stop(label: String): String {
        context.getSystemService(SensorManager::class.java).unregisterListener(listener)
        if (times.isEmpty()) {
            return "raw sensor: $sensorDescription\nraw events=0: the raw sensor delivered no events to this app " +
                "(${error.ifEmpty { "registered, but nothing arrived" }})\n"
        }
        PocFiles.write(
            context,
            "watch-raw-$label.csv",
            buildString {
                for (i in times.indices) append(times[i]).append(',').append(values[i].joinToString(",")).append('\n')
            }
        )
        return report()
    }

    private fun report(): String = buildString {
        val n = times.size
        val seconds = (times.last() - times.first()) / NANOS_PER_SECOND
        val rate = if (seconds > 0) (n - 1) / seconds else 0.0
        val channels = values.first().size
        appendLine("raw sensor: $sensorDescription")
        appendLine("raw events=$n duration=${fmt(seconds)} s rate=${fmt(rate)} Hz channels=$channels")
        val t = times.toLongArray()
        for (c in 0 until minOf(channels, MAX_CHANNELS)) {
            val v = DoubleArray(n) { values[it][c].toDouble() }
            val beats = BeatDetector.beatTimes(t, v)
            val ibis = Hrv.clean(BeatDetector.intervalsMs(beats))
            val m = Hrv.metrics(ibis)
            append("raw channel $c: min=${fmt(v.min())} max=${fmt(v.max())} distinct=${v.distinct().size} ")
            append("beatsFound=${beats.size} intervalsKept=${ibis.size} ")
            appendLine(
                if (m == null) {
                    "HRV: not computable (too few usable beat-to-beat intervals)"
                } else {
                    "meanIbi=${fmt(m.meanIbiMs)} ms meanBpm=${fmt(m.meanBpm)} sdnn=${fmt(m.sdnnMs)} ms " +
                        "rmssd=${fmt(m.rmssdMs)} ms pnn50=${fmt(m.pnn50Percent)} %"
                }
            )
        }
    }

    private fun describe(s: Sensor): String =
        "${s.name} type=${s.stringType}(${s.type}) vendor=${s.vendor} minDelayUs=${s.minDelay} " +
            "reporting=${s.reportingMode} resolution=${s.resolution} maxRange=${s.maximumRange} wakeUp=${s.isWakeUpSensor}"

    private fun fmt(v: Double): String = "%.1f".format(java.util.Locale.ROOT, v)

    private companion object {
        const val RAW_TYPE = "com.samsung.sensor.hr_raw"
        const val MAX_EVENTS = 20_000
        const val MAX_CHANNELS = 4
        const val NANOS_PER_SECOND = 1e9
        val HEART_WORDS = Regex("(?i)heart|\\bhr\\b|hr_|ppg")
        val RAW_NAME_WORDS = Regex("(?i)hr raw|ppg")
    }
}

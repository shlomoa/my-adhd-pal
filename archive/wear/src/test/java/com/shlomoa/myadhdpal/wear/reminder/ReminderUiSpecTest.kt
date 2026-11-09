package com.shlomoa.myadhdpal.wear.reminder

import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.wear.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReminderUiSpecTest {
    @Test
    fun `TC-W-030 the haptic catalogue has tick, warning and calm-breath with distinct patterns`() {
        val patterns = Haptic.entries.associateWith { it.pattern }
        assertEquals(setOf("TICK", "WARNING", "CALM_BREATH"), Haptic.entries.map { it.name }.toSet())
        assertEquals(3, patterns.values.toSet().size)
        patterns.values.forEach { assertEquals(it.timingsMs.size, it.amplitudes.size) }
    }

    @Test
    fun `TC-W-030 each haptic pattern starts with a pause and has positive durations`() {
        Haptic.entries.forEach { haptic ->
            val timings = haptic.pattern.timingsMs
            assertEquals(0L, timings.first())
            assertEquals(true, timings.drop(1).all { it > 0 })
            assertNotEquals(0, timings.size)
        }
    }

    @Test
    fun `TC-W-031 the reminder notification has the actions Done, Snooze, Start 2 min and Dismiss`() {
        assertEquals(
            listOf(AckAction.DONE, AckAction.SNOOZE, AckAction.START_2_MIN, AckAction.DISMISS),
            ReminderAction.entries.map { it.ack }
        )
        assertEquals(
            listOf(R.string.action_done, R.string.action_snooze, R.string.action_start_2_min, R.string.action_dismiss),
            ReminderAction.entries.map { it.labelRes }
        )
        val strings = File("src/main/res/values/strings.xml").readText()
        listOf(
            "action_done" to "Done",
            "action_snooze" to "Snooze",
            "action_start_2_min" to "Start 2 min",
            "action_dismiss" to "Dismiss"
        )
            .forEach { (name, text) ->
                assertEquals(true, Regex("""<string name="$name">$text</string>""").containsMatchIn(strings))
            }
    }
}

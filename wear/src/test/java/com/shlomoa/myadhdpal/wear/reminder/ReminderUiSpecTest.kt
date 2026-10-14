package com.shlomoa.myadhdpal.wear.reminder

import com.shlomoa.myadhdpal.protocol.AckAction
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
        val strings = File("src/main/res/values/strings.xml").readText()
        val labels = ReminderAction.entries.map { it.labelResName }
        assertEquals(listOf("action_done", "action_snooze", "action_start_2_min", "action_dismiss"), labels)
        val expected = listOf("Done", "Snooze", "Start 2 min", "Dismiss")
        labels.zip(expected).forEach { (name, text) ->
            assertEquals(true, Regex("""<string name="$name">$text</string>""").containsMatchIn(strings))
        }
    }
}

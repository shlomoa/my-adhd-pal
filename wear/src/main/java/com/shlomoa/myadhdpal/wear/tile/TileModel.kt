package com.shlomoa.myadhdpal.wear.tile

import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.wear.link.LinkView
import com.shlomoa.myadhdpal.wear.scheduler.SnoozeEntry
import com.shlomoa.myadhdpal.wear.scheduler.nextDue
import java.time.Instant
import java.time.ZoneId

/** What a tap on the tile's button does: start the two minutes for this reminder occurrence. */
data class TileAction(val reminderId: String, val occurrenceAt: Instant) {
    fun toExtras(): Map<String, String> =
        mapOf(EXTRA_REMINDER_ID to reminderId, EXTRA_OCCURRENCE_AT to occurrenceAt.toEpochMilli().toString())

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OCCURRENCE_AT = "occurrence_at"

        /** Null for missing or malformed extras: the tile activity is exported, so its input is not trusted. */
        fun fromExtras(extras: Map<String, String?>): TileAction? {
            val id = extras[EXTRA_REMINDER_ID]
            val at = extras[EXTRA_OCCURRENCE_AT]?.toLongOrNull()
            return if (id.isNullOrBlank() || at == null) null else TileAction(id, Instant.ofEpochMilli(at))
        }
    }
}

data class TileReminder(val title: String, val at: Instant)

/** Everything the tile shows, as data; the layout code only draws it. */
data class TileModel(val reminder: TileReminder?, val action: TileAction?, val link: LinkView)

fun tileModel(
    schedule: Schedule?,
    snoozes: Map<String, SnoozeEntry>,
    link: LinkView,
    now: Instant,
    zone: ZoneId
): TileModel {
    val due = nextDue(schedule, snoozes, now, zone)
    return TileModel(
        reminder = due?.let { TileReminder(it.reminder.title, it.at) },
        action = due?.let { TileAction(it.reminder.id, it.occurrenceAt) },
        link = link
    )
}

package com.shlomoa.myadhdpal.wear.schedule

import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.wear.log.Logger
import java.io.File
import java.io.IOException
import java.time.Instant

/** The schedule in use and the watch-clock time it was applied (shown as the last sync). */
data class StoredSchedule(val schedule: Schedule, val appliedAt: Instant)

/** Keeps the applied schedule so that reminders keep working without the phone, also after a restart or reboot. */
interface ScheduleStore {
    fun load(): StoredSchedule?

    fun save(schedule: StoredSchedule)
}

/**
 * A [ScheduleStore] in one file: the apply time in epoch milliseconds on the first line, then the schedule body as the
 * protocol codec writes it. A file that cannot be read reads as nothing stored. Writes go to a temporary file that is
 * then renamed, so a crash never leaves a half-written schedule.
 */
class FileScheduleStore(private val file: File) : ScheduleStore {
    override fun load(): StoredSchedule? {
        val text = readOrNull() ?: return null
        val appliedAt = text.substringBefore('\n').toLongOrNull()
        val body = Envelope(Protocol.MAJOR_VERSION, STORED_ID, 0, text.substringAfter('\n'))
        val schedule = (Codec.decode(Paths.SCHEDULE, body) as? DecodeResult.Ok)?.item?.message as? Schedule
        val stored = if (appliedAt != null &&
            schedule != null
        ) {
            StoredSchedule(schedule, Instant.ofEpochMilli(appliedAt))
        } else {
            null
        }
        if (stored == null) Logger.w(TAG, "stored schedule is corrupt and is ignored")
        return stored
    }

    private fun readOrNull(): String? = try {
        if (file.exists()) file.readText() else null
    } catch (e: IOException) {
        Logger.w(TAG, "schedule file unreadable: ${e.javaClass.simpleName}")
        null
    }

    override fun save(schedule: StoredSchedule) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(schedule.appliedAt.toEpochMilli().toString() + "\n" + Codec.encodeBody(schedule.schedule))
        if (!temporary.renameTo(file)) throw IOException("cannot replace ${file.name}")
    }

    private companion object {
        const val TAG = "FileScheduleStore"
        const val STORED_ID = "stored"
    }
}

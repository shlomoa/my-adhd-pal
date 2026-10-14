package com.shlomoa.myadhdpal.wear.scheduler

import com.shlomoa.myadhdpal.wear.log.Logger
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * A [SnoozeStore] in one file, a line per snooze: reminder id, due time in epoch milliseconds and snooze count,
 * separated by tabs. Lines that cannot be read are skipped. Written to a temporary file that is then renamed.
 */
class FileSnoozeStore(private val file: File) : SnoozeStore {
    override fun load(): Map<String, SnoozeEntry> {
        val lines = try {
            if (file.exists()) file.readLines() else emptyList()
        } catch (e: IOException) {
            Logger.w(TAG, "snooze file unreadable: ${e.javaClass.simpleName}")
            emptyList()
        }
        return lines.mapNotNull(::parse).toMap()
    }

    override fun save(entries: Map<String, SnoozeEntry>) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(
            entries.entries.joinToString("\n") {
                "${it.key}\t${it.value.dueAt.toEpochMilli()}\t${it.value.count}"
            }
        )
        if (!temporary.renameTo(file)) throw IOException("cannot replace ${file.name}")
    }

    private fun parse(line: String): Pair<String, SnoozeEntry>? {
        val parts = line.split('\t')
        val dueAt = parts.getOrNull(1)?.toLongOrNull()
        val count = parts.getOrNull(2)?.toIntOrNull()
        return if (parts.size == PARTS && dueAt != null && count != null) {
            parts[0] to SnoozeEntry(Instant.ofEpochMilli(dueAt), count)
        } else {
            null
        }
    }

    private companion object {
        const val TAG = "FileSnoozeStore"
        const val PARTS = 3
    }
}

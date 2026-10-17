package com.shlomoa.myadhdpal.wear.scheduler

import com.shlomoa.myadhdpal.wear.log.Logger
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * A [SnoozeStore] in one file, a line per snooze: reminder id, due time in epoch milliseconds, snooze count and
 * occurrence time in epoch milliseconds, separated by tabs. Lines that cannot be read are skipped. Written to a
 * temporary file that is then renamed.
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
            entries.entries.joinToString("\n") { (id, entry) -> line(id, entry) }
        )
        if (!temporary.renameTo(file)) throw IOException("cannot replace ${file.name}")
    }

    private fun line(id: String, entry: SnoozeEntry) =
        listOf(id, entry.dueAt.toEpochMilli(), entry.count, entry.occurrenceAt.toEpochMilli()).joinToString("\t")

    private fun parse(line: String): Pair<String, SnoozeEntry>? {
        val parts = line.split('\t')
        val numbers = parts.drop(1).map { it.toLongOrNull() }
        if (parts.size != PARTS || numbers.any { it == null }) return null
        val (dueAt, count, occurrenceAt) = numbers.map { it!! }
        return parts[0] to SnoozeEntry(Instant.ofEpochMilli(dueAt), count.toInt(), Instant.ofEpochMilli(occurrenceAt))
    }

    private companion object {
        const val TAG = "FileSnoozeStore"
        const val PARTS = 4
    }
}

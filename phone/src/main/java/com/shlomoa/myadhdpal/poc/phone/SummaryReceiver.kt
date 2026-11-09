package com.shlomoa.myadhdpal.poc.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shlomoa.myadhdpal.poc.core.Sample
import com.shlomoa.myadhdpal.poc.core.Stats
import java.io.File

/**
 * Computes the latency summary for every phone-<label>.csv and writes phone-summary-<label>.txt.
 *
 * end_to_end = phone arrival - (recorded time + offset), where offset (phone clock minus watch clock) is the
 * watch's estimate carried in each reading. Readings sent before the first ping/pong finished have no offset and
 * are counted but left out of the latency statistics.
 */
class SummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        File(context.filesDir.path).listFiles { f -> f.name.startsWith("phone-") && f.name.endsWith(".csv") }
            ?.forEach { csv ->
                // phone-<label>-<path>.csv; a label may contain '-', the path (dl or bt) is the last part.
                val core = csv.name.removePrefix("phone-").removeSuffix(".csv")
                val label = core.substringBeforeLast('-')
                val path = core.substringAfterLast('-')
                PhoneState.write(context, "phone-summary-$core.txt", summarize(label, path, csv.readLines()))
            }
    }

    private fun summarize(label: String, path: String, lines: List<String>): String {
        val rows = lines.mapNotNull { line ->
            val f = line.split(',')
            if (f.size < FIELDS) return@mapNotNull null
            runCatching {
                Row(f[1].toLong(), f[3].toLong(), f[4].toLong(), f[5].toLong(), f[6].toLong(), f[IDX_SCREEN])
            }.getOrNull()
        }
        val withOffset = rows.filter { it.offset != Sample.NO_OFFSET }
        val endToEnd = withOffset.map { it.phoneArrival - (it.source + it.offset) }
        val linkOnly = withOffset.map { it.phoneArrival - (it.send + it.offset) }
        val gaps = Stats.gaps(rows.map { it.phoneArrival }.sorted())
        return buildString {
            appendLine("label=$label path=$path received=${rows.size} withClockOffset=${withOffset.size}")
            appendLine(Stats.line("end_to_end (recorded -> phone)", Stats.summarize(endToEnd)))
            appendLine(Stats.line("watch_send_to_phone", Stats.summarize(linkOnly)))
            appendLine(Stats.line("gap_between_phone_arrivals", Stats.summarize(gaps)))
            for ((screen, group) in withOffset.groupBy { it.screen }.toSortedMap()) {
                val values = group.map { it.phoneArrival - (it.source + it.offset) }
                appendLine(Stats.line("end_to_end while watch screen=$screen", Stats.summarize(values)))
            }
        }
    }

    private data class Row(
        val seq: Long,
        val source: Long,
        val send: Long,
        val offset: Long,
        val phoneArrival: Long,
        val screen: String
    )

    private companion object {
        const val FIELDS = 8
        const val IDX_SCREEN = 7
    }
}

package com.shlomoa.myadhdpal.wear.schedule

import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.ScheduleLogic
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.TransportListener
import com.shlomoa.myadhdpal.wear.demo.DemoLog
import com.shlomoa.myadhdpal.wear.log.Logger
import java.time.Instant

/**
 * Applies `/schedule` items from the phone: an item is used only if it decodes, has a supported major version and
 * carries a newer version than the schedule in use. The applied schedule is saved and [onApplied] is told (re-arm
 * the reminders, update the last-sync time). The phone writes `/schedule` and owns its cleanup: the watch never
 * deletes it.
 */
class ScheduleReceiver(
    private val transport: LinkTransport,
    private val store: ScheduleStore,
    private val now: () -> Instant,
    private val onApplied: (Schedule, Instant) -> Unit
) : TransportListener {
    /** The schedule in use, loaded from storage at construction. */
    var current: Schedule? = store.load()?.schedule
        private set

    /** Applies a `/schedule` item that is already on the node, as after the process started with data waiting. */
    fun start() {
        transport.items().filter { it.path == Paths.SCHEDULE }.forEach { onItemChanged(it) }
    }

    override fun onItemChanged(item: StoredItem) {
        if (item.path != Paths.SCHEDULE) return
        when (val decoded = Codec.decodeIfCompatible(item.path, item.envelope)) {
            is DecodeResult.Failure -> Logger.w(TAG, "schedule item dropped: ${decoded.reason}")
            is DecodeResult.Ok -> (decoded.item.message as? Schedule)?.let { apply(it) }
        }
    }

    override fun onItemDeleted(path: String) = Unit

    override fun onLinkStateChanged(state: LinkState) = Unit

    private fun apply(schedule: Schedule) {
        if (!ScheduleLogic.shouldApply(current?.version, schedule.version)) {
            Logger.d(TAG, "schedule ${schedule.version} ignored, ${current?.version} is in use")
            return
        }
        val appliedAt = now()
        store.save(StoredSchedule(schedule, appliedAt))
        current = schedule
        DemoLog.scheduleApplied(schedule.version)
        onApplied(schedule, appliedAt)
    }

    private companion object {
        const val TAG = "ScheduleReceiver"
    }
}

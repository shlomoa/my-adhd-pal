package com.shlomoa.myadhdpal.wear.reminder

import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.SnoozeDenial
import com.shlomoa.myadhdpal.protocol.SnoozeResult
import com.shlomoa.myadhdpal.wear.log.Logger
import com.shlomoa.myadhdpal.wear.scheduler.ReminderScheduler
import java.time.Instant

/** A countdown that vibrates when it ends (the "Start 2 min" action). */
interface TimerPort {
    fun start(reminderId: String, seconds: Int)
}

sealed interface ActionOutcome {
    data object Done : ActionOutcome

    /** The same action on the same notification was already handled. */
    data object Duplicate : ActionOutcome

    /** Snoozing is not allowed (the cap is reached); nothing was changed or sent. */
    data class SnoozeDenied(val reason: SnoozeDenial) : ActionOutcome
}

/**
 * What a tap on a reminder notification does: the local effect first (snooze moves the alarm, Start 2 min starts the
 * timer, Done and Dismiss end a pending snooze), then an `/ack/<id>` item for the phone. The item is a fresh UUID per
 * event; the transport buffers it while the phone is away, and the phone deletes it after storing it (the watch only
 * writes its own items). Without a transport the local effect still happens and the missing ack is logged.
 * [onHandled] is called after an action was carried out (not for a duplicate or a refused snooze).
 */
class ReminderActionHandler(
    private val scheduler: ReminderScheduler,
    private val transport: () -> LinkTransport?,
    private val timer: TimerPort,
    private val now: () -> Instant,
    private val newId: () -> String,
    private val onHandled: () -> Unit = {}
) {
    private val handled = LinkedHashSet<String>()

    fun handle(action: ReminderAction, reminderId: String, snoozeCount: Int, occurrenceAt: Instant): ActionOutcome {
        val key = "$reminderId/${occurrenceAt.toEpochMilli()}/$snoozeCount/${action.name}"
        return if (remember(key)) perform(action, reminderId, snoozeCount, occurrenceAt) else ActionOutcome.Duplicate
    }

    private fun perform(
        action: ReminderAction,
        reminderId: String,
        snoozeCount: Int,
        occurrenceAt: Instant
    ): ActionOutcome {
        val denied = applyLocally(action, reminderId, snoozeCount, occurrenceAt)
        if (denied != null) return denied
        acknowledge(action, reminderId, occurrenceAt)
        onHandled()
        return ActionOutcome.Done
    }

    /** The effect on the watch itself; returns the outcome only when the action was refused. */
    private fun applyLocally(
        action: ReminderAction,
        reminderId: String,
        snoozeCount: Int,
        occurrenceAt: Instant
    ): ActionOutcome? = when (action) {
        ReminderAction.SNOOZE ->
            (scheduler.snooze(reminderId, snoozeCount, occurrenceAt) as? SnoozeResult.Denied)
                ?.let { ActionOutcome.SnoozeDenied(it.reason) }

        ReminderAction.START_2_MIN -> {
            scheduler.clearSnooze(reminderId)
            timer.start(reminderId, TWO_MINUTES_SECONDS)
            null
        }

        ReminderAction.DONE, ReminderAction.DISMISS -> {
            scheduler.clearSnooze(reminderId)
            null
        }
    }

    private fun acknowledge(action: ReminderAction, reminderId: String, occurrenceAt: Instant) {
        val link = transport()
        if (link == null) {
            Logger.w(TAG, "no transport yet, the acknowledgement was not sent")
            return
        }
        val at = now().toEpochMilli()
        val message = Acknowledgement(action.ack, at, reminderId, occurrenceAt.toEpochMilli())
        val id = newId()
        link.put(Paths.ack(id), Codec.encode(id, at, message))
    }

    private fun remember(key: String): Boolean {
        if (handled.size >= MAX_REMEMBERED) handled.remove(handled.first())
        return handled.add(key)
    }

    private companion object {
        const val TAG = "ReminderActionHandler"
        const val TWO_MINUTES_SECONDS = 120
        const val MAX_REMEMBERED = 100
    }
}

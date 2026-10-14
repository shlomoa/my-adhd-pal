package com.shlomoa.myadhdpal.wear.reminder

import androidx.annotation.StringRes
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.wear.R

/** The actions on a reminder notification and the acknowledgement each one reports. */
enum class ReminderAction(val ack: AckAction, @StringRes val labelRes: Int) {
    DONE(AckAction.DONE, R.string.action_done),
    SNOOZE(AckAction.SNOOZE, R.string.action_snooze),
    START_2_MIN(AckAction.START_2_MIN, R.string.action_start_2_min),
    DISMISS(AckAction.DISMISS, R.string.action_dismiss)
}

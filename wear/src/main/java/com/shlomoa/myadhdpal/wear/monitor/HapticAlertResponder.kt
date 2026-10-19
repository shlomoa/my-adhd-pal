package com.shlomoa.myadhdpal.wear.monitor

import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.wear.reminder.Haptic
import com.shlomoa.myadhdpal.wear.reminder.HapticPlayer

/** The local response to a stress event: the firm warning vibration (the Monitoring screen shows the state). */
class HapticAlertResponder(private val haptics: HapticPlayer) : AlertResponder {
    override fun respond(alert: Alert) = haptics.play(Haptic.WARNING)
}

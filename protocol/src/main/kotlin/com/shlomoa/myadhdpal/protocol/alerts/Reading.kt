package com.shlomoa.myadhdpal.protocol.alerts

import com.shlomoa.myadhdpal.protocol.Metric

/** One measurement; [at] is epoch milliseconds on the clock of the device that measured it. */
data class Reading(val metric: Metric, val value: Double, val at: Long)

package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.protocol.WatchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The newest `/status/watch` item wins; an older one that arrives late does not replace it. */
class LatestStatus {
    private val current = MutableStateFlow<WatchStatus?>(null)
    private var at = Long.MIN_VALUE

    val state: StateFlow<WatchStatus?> = current

    /** True when [status] was taken (it is not older than the one held). */
    @Synchronized
    fun accept(status: WatchStatus, timestamp: Long): Boolean {
        if (timestamp < at) return false
        at = timestamp
        current.value = status
        return true
    }
}

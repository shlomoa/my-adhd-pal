package com.shlomoa.myadhdpal.wear.tile

import java.time.Duration
import java.time.Instant

private val MIN_FRESHNESS = Duration.ofMinutes(1)
private val MAX_FRESHNESS = Duration.ofHours(24)

/**
 * How long a tile stays valid: until the next reminder is due, when the tile has to show the one after it. Never
 * shorter than a minute (a due or past time would loop) and never longer than a day; with no reminder, a day.
 */
fun tileFreshness(nextDueAt: Instant?, now: Instant): Duration {
    if (nextDueAt == null) return MAX_FRESHNESS
    val until = Duration.between(now, nextDueAt)
    return maxOf(MIN_FRESHNESS, minOf(MAX_FRESHNESS, until))
}

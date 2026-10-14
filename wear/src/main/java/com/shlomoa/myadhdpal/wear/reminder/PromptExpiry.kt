package com.shlomoa.myadhdpal.wear.reminder

import com.shlomoa.myadhdpal.protocol.Prompt

/**
 * A prompt's `expiresAt` is epoch milliseconds on the phone clock. The phone deletes expired prompts when it can; the
 * watch also never shows one whose expiry is at or before its own clock [nowMillis], and hides a shown one when its
 * clock reaches the expiry (a few seconds of clock difference is accepted, docs/protocol.md).
 */
fun Prompt.isExpiredAt(nowMillis: Long): Boolean = expiresAt?.let { it <= nowMillis } ?: false

/** Milliseconds until the watch clock reaches the expiry (0 once reached), or null for a prompt that never expires. */
fun Prompt.millisUntilExpiry(nowMillis: Long): Long? = expiresAt?.let { (it - nowMillis).coerceAtLeast(0L) }

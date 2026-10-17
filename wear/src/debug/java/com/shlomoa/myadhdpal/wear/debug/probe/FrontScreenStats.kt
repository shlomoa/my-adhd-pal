package com.shlomoa.myadhdpal.wear.debug.probe

/** The debug screen is in front when it is resumed and its window has focus. */
fun isInFront(resumed: Boolean, hasFocus: Boolean): Boolean = resumed && hasFocus

/** One `PROBE front` line: event, screen state, in front, keep-screen-on. No heart-rate values. */
fun frontLine(event: String, displayState: Int, inFront: Boolean, keepOn: Boolean): String =
    "PROBE front $event screen=${displayName(displayState)} inFront=$inFront keepOn=$keepOn"

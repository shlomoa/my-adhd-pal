package com.shlomoa.myadhdpal.wear.reminder

class FakeTimerPort : TimerPort {
    data class Started(val reminderId: String, val seconds: Int)

    val started = mutableListOf<Started>()

    override fun start(reminderId: String, seconds: Int) {
        started += Started(reminderId, seconds)
    }
}

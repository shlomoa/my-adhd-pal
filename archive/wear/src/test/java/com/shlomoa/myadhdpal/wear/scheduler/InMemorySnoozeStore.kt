package com.shlomoa.myadhdpal.wear.scheduler

class InMemorySnoozeStore : SnoozeStore {
    private var entries: Map<String, SnoozeEntry> = emptyMap()

    override fun load(): Map<String, SnoozeEntry> = entries

    override fun save(entries: Map<String, SnoozeEntry>) {
        this.entries = entries
    }
}

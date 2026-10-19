package com.shlomoa.myadhdpal.phone.ui

/** The tabs of the home screen. [named] reads one from text, for the demo script's `--es tab history`. */
enum class HomeTab {
    WATCH,
    TASKS,
    ASK,
    SETTINGS,
    HISTORY;

    companion object {
        fun named(name: String?): HomeTab = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: WATCH
    }
}

package com.shlomoa.myadhdpal.phone.ui

/** What a list screen shows: loading, nothing yet, the items, or a failure the user can retry. */
sealed interface ListState<out T> {
    data object Loading : ListState<Nothing>

    data object Empty : ListState<Nothing>

    data class Content<T>(val items: List<T>) : ListState<T>

    data class Failed(val reason: String) : ListState<Nothing>
}

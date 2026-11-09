package com.shlomoa.myadhdpal.phone.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.ui.ListState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** [filter] null means all types. */
data class HistoryUiState(val filter: HistoryType?, val entries: ListState<HistoryEntry>)

/**
 * Step 4.5: acknowledgements, selections and alerts, newest first. Becomes a `@HiltViewModel` when the real
 * [HistoryRepository] is bound (step 2.5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(private val history: HistoryRepository) : ViewModel() {
    private val filter = MutableStateFlow<HistoryType?>(null)
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<HistoryUiState> = combine(filter, attempt) { type, _ -> type }
        .flatMapLatest { type ->
            history.history(type)
                .map<List<HistoryEntry>, ListState<HistoryEntry>> {
                    if (it.isEmpty()) ListState.Empty else ListState.Content(it)
                }
                .onStart { emit(ListState.Loading) }
                .catch { emit(ListState.Failed(it.message ?: "history unavailable")) }
                .map { HistoryUiState(type, it) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState(null, ListState.Loading))

    fun setFilter(type: HistoryType?) {
        filter.value = type
    }

    /** Reads again after a failure. */
    fun retry() {
        attempt.value++
    }
}

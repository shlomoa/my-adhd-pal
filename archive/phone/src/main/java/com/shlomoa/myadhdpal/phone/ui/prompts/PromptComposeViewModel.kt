package com.shlomoa.myadhdpal.phone.ui.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shlomoa.myadhdpal.phone.data.PromptError
import com.shlomoa.myadhdpal.phone.data.PromptPublishResult
import com.shlomoa.myadhdpal.phone.data.PromptRepository
import com.shlomoa.myadhdpal.protocol.Limits
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PromptStatus {
    data object Idle : PromptStatus

    data object Sending : PromptStatus

    data object Sent : PromptStatus

    data class Failed(val reason: String) : PromptStatus
}

/** The Ask form: a question, 2 to [Limits.MAX_OPTIONS] answers, and how long the question stays open. */
data class PromptComposeUiState(
    val title: String = "",
    val options: List<String> = List(MIN_OPTIONS) { "" },
    val expiryMinutes: Int? = null,
    val errors: Set<PromptError> = emptySet(),
    val status: PromptStatus = PromptStatus.Idle
) {
    companion object {
        const val MIN_OPTIONS = 2
    }
}

/** Step 4.4: validates through [PromptRepository] and publishes `/prompt/<id>`; empty answers are left out. */
@HiltViewModel
class PromptComposeViewModel @Inject constructor(private val prompts: PromptRepository, private val clock: Clock) :
    ViewModel() {
    private val state = MutableStateFlow(PromptComposeUiState())
    val uiState: StateFlow<PromptComposeUiState> = state

    fun setTitle(title: String) = edit { copy(title = title) }

    fun setOption(index: Int, label: String) =
        edit { copy(options = options.mapIndexed { i, old -> if (i == index) label else old }) }

    fun addOption() = edit { if (options.size < Limits.MAX_OPTIONS) copy(options = options + "") else this }

    fun removeOption(index: Int) = edit {
        if (options.size >
            PromptComposeUiState.MIN_OPTIONS
        ) {
            copy(options = options.filterIndexed { i, _ -> i != index })
        } else {
            this
        }
    }

    fun setExpiry(minutes: Int?) = edit { copy(expiryMinutes = minutes) }

    fun send() {
        val form = state.value
        if (form.status == PromptStatus.Sending) return
        state.update { it.copy(status = PromptStatus.Sending, errors = emptySet()) }
        viewModelScope.launch {
            val expiresAt = form.expiryMinutes?.let { clock.instant().plus(Duration.ofMinutes(it.toLong())) }
            val labels = form.options.map { it.trim() }.filter { it.isNotEmpty() }
            state.value = when (val result = prompts.publish(form.title.trim(), labels, expiresAt)) {
                is PromptPublishResult.Published -> PromptComposeUiState(status = PromptStatus.Sent)
                is PromptPublishResult.Rejected -> form.copy(errors = result.errors, status = PromptStatus.Idle)
                is PromptPublishResult.Failed -> form.copy(status = PromptStatus.Failed(result.reason))
            }
        }
    }

    private fun edit(change: PromptComposeUiState.() -> PromptComposeUiState) =
        state.update { it.change().copy(errors = emptySet(), status = PromptStatus.Idle) }
}

package com.selinuxtoolbox.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.GetRecentDenialsUseCase
import com.selinuxtoolbox.core.domain.usecase.GetSelinuxStatusUseCase
import com.selinuxtoolbox.core.domain.usecase.SelinuxStatus
import com.selinuxtoolbox.core.domain.usecase.SetSelinuxModeUseCase
import com.selinuxtoolbox.core.domain.usecase.StreamAvcDenialsUseCase
import com.selinuxtoolbox.core.model.AvcDenial
import com.selinuxtoolbox.core.model.Project
import com.selinuxtoolbox.core.model.SelinuxMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val selinuxStatus: SelinuxStatus? = null,
    val recentDenials: List<AvcDenial> = emptyList(),
    val activeProject: Project? = null,
    val isLoadingStatus: Boolean = false,
    val isLoadingDenials: Boolean = false,
    val isStreaming: Boolean = false,
    val streamedDenialCount: Int = 0,
    val error: String? = null
)

sealed class DashboardEvent {
    data class Error(val message: String) : DashboardEvent()
    data class ModeChanged(val enforcing: Boolean) : DashboardEvent()
    object StreamStarted : DashboardEvent()
    object StreamStopped : DashboardEvent()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getSelinuxStatusUseCase: GetSelinuxStatusUseCase,
    private val setSelinuxModeUseCase: SetSelinuxModeUseCase,
    private val getRecentDenialsUseCase: GetRecentDenialsUseCase,
    private val streamAvcDenialsUseCase: StreamAvcDenialsUseCase,
    private val getActiveProjectUseCase: GetActiveProjectUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DashboardEvent>()
    val events: SharedFlow<DashboardEvent> = _events.asSharedFlow()

    // Holds the streaming job so we can cancel it
    private var streamingJob: Job? = null

    // Max denials to keep in the live list to avoid unbounded memory growth
    private val MAX_LIVE_DENIALS = 200

    init {
        refresh()
        observeActiveProject()
    }

    // -------------------------------------------------------------------------
    // Initial load and refresh
    // -------------------------------------------------------------------------

    fun refresh() {
        loadSelinuxStatus()
        loadRecentDenials()
    }

    private fun loadSelinuxStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStatus = true, error = null) }
            try {
                val status = getSelinuxStatusUseCase()
                _uiState.update { it.copy(selinuxStatus = status, isLoadingStatus = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingStatus = false,
                        error = "Failed to get SELinux status: ${e.message}"
                    )
                }
            }
        }
    }

    private fun loadRecentDenials() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDenials = true) }
            try {
                val denials = getRecentDenialsUseCase(limit = 50)
                _uiState.update {
                    it.copy(recentDenials = denials, isLoadingDenials = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDenials = false) }
            }
        }
    }

    private fun observeActiveProject() {
        viewModelScope.launch {
            getActiveProjectUseCase().collect { project ->
                _uiState.update { it.copy(activeProject = project) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SELinux mode toggle
    // -------------------------------------------------------------------------

    fun toggleSelinuxMode() {
        val currentMode = _uiState.value.selinuxStatus?.mode ?: return
        val setEnforcing = currentMode != SelinuxMode.ENFORCING

        viewModelScope.launch {
            val success = setSelinuxModeUseCase(setEnforcing)
            if (success) {
                _events.emit(DashboardEvent.ModeChanged(setEnforcing))
                // Refresh status to reflect the change
                loadSelinuxStatus()
            } else {
                _events.emit(
                    DashboardEvent.Error("Failed to change SELinux mode — root access required")
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Live denial streaming
    // -------------------------------------------------------------------------

    fun startStreaming() {
        if (_uiState.value.isStreaming) return

        streamingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStreaming = true,
                    streamedDenialCount = 0,
                    // Clear old one-shot denials when streaming starts
                    recentDenials = emptyList()
                )
            }
            _events.emit(DashboardEvent.StreamStarted)

            try {
                streamAvcDenialsUseCase().collect { denial ->
                    _uiState.update { state ->
                        val updated = (listOf(denial) + state.recentDenials)
                            .take(MAX_LIVE_DENIALS)
                        state.copy(
                            recentDenials = updated,
                            streamedDenialCount = state.streamedDenialCount + 1
                        )
                    }
                }
            } catch (e: Exception) {
                _events.emit(DashboardEvent.Error("Stream error: ${e.message}"))
            } finally {
                _uiState.update { it.copy(isStreaming = false) }
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        _uiState.update { it.copy(isStreaming = false) }
        viewModelScope.launch { _events.emit(DashboardEvent.StreamStopped) }
        // Reload static snapshot after streaming stops
        loadRecentDenials()
    }

    // -------------------------------------------------------------------------
    // Clear denials list
    // -------------------------------------------------------------------------

    fun clearDenials() {
        _uiState.update { it.copy(recentDenials = emptyList(), streamedDenialCount = 0) }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
    }
}

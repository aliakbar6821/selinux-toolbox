package com.selinuxtoolbox.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.analyzer.CleanupResult
import com.selinuxtoolbox.core.domain.usecase.CleanupPhase
import com.selinuxtoolbox.core.domain.usecase.RunCleanupUseCase
import com.selinuxtoolbox.core.model.LoadedPolicy
import com.selinuxtoolbox.core.model.OperationProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── UI State ────────────────────────────────────────────────────────────────

data class CleanupUiState(
    val phase: CleanupUiPhase = CleanupUiPhase.Idle,
    val progress: OperationProgress? = null,
    val result: CleanupResult? = null,
    val outputDir: String? = null,
    val filesModified: Int = 0,
    val error: String? = null
)

sealed class CleanupUiPhase {
    object Idle : CleanupUiPhase()
    object Analyzing : CleanupUiPhase()
    object ReviewingResults : CleanupUiPhase()
    object Applying : CleanupUiPhase()
    object Done : CleanupUiPhase()
    data class Failed(val reason: String) : CleanupUiPhase()
}

sealed class CleanupEvent {
    data class Error(val message: String) : CleanupEvent()
    data class SavedSuccess(val outputDir: String, val filesModified: Int) : CleanupEvent()
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class CleanupViewModel @Inject constructor(
    private val runCleanupUseCase: RunCleanupUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanupUiState())
    val uiState: StateFlow<CleanupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CleanupEvent>()
    val events: SharedFlow<CleanupEvent> = _events.asSharedFlow()

    // Active project folder path — read from preferences
    val outputPath: StateFlow<String> = appPreferences.outputFolderPath
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "/sdcard/SELinuxToolbox"
        )

    // ── Phase 1: analyze ─────────────────────────────────────────────────────

    fun startAnalysis(
        policy: LoadedPolicy,
        projectId: Long,
        socPrefixes: List<String> = emptyList(),
        romPrefixes: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = CleanupUiPhase.Analyzing, error = null) }

            runCleanupUseCase.analyze(policy, socPrefixes, romPrefixes).collect { phase ->
                when (phase) {
                    is CleanupPhase.Analyzing -> {
                        _uiState.update {
                            it.copy(progress = phase.progress)
                        }
                    }
                    is CleanupPhase.Complete -> {
                        _uiState.update {
                            it.copy(
                                phase = CleanupUiPhase.ReviewingResults,
                                result = phase.result,
                                progress = null
                            )
                        }
                    }
                    is CleanupPhase.Failed -> {
                        _uiState.update {
                            it.copy(
                                phase = CleanupUiPhase.Failed(phase.reason),
                                error = phase.reason
                            )
                        }
                        _events.emit(CleanupEvent.Error(phase.reason))
                    }
                    else -> { /* ApplyingChanges / Saved handled in applyChanges */ }
                }
            }
        }
    }

    // ── Phase 2: apply confirmed results ─────────────────────────────────────

    fun applyChanges(
        projectId: Long,
        policy: LoadedPolicy,
        projectFolderPath: String
    ) {
        val result = _uiState.value.result ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(phase = CleanupUiPhase.Applying) }

            val phase = runCleanupUseCase.applyAndSave(
                projectId = projectId,
                policy = policy,
                result = result,
                projectFolderPath = projectFolderPath
            )

            when (phase) {
                is CleanupPhase.Saved -> {
                    _uiState.update {
                        it.copy(
                            phase = CleanupUiPhase.Done,
                            outputDir = phase.outputDir,
                            filesModified = phase.filesModified
                        )
                    }
                    _events.emit(
                        CleanupEvent.SavedSuccess(phase.outputDir, phase.filesModified)
                    )
                }
                is CleanupPhase.Failed -> {
                    _uiState.update {
                        it.copy(
                            phase = CleanupUiPhase.Failed(phase.reason),
                            error = phase.reason
                        )
                    }
                    _events.emit(CleanupEvent.Error(phase.reason))
                }
                else -> {}
            }
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        _uiState.update { CleanupUiState() }
    }
}

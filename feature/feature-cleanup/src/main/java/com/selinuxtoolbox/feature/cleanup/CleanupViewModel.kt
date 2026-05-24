package com.selinuxtoolbox.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.domain.analyzer.CleanupResult
import com.selinuxtoolbox.core.domain.usecase.CleanupPhase
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.GetLivePolicyUseCase
import com.selinuxtoolbox.core.domain.usecase.RunCleanupUseCase
import com.selinuxtoolbox.core.model.LoadedPolicy
import com.selinuxtoolbox.core.model.OperationProgress
import com.selinuxtoolbox.core.model.Project
import com.selinuxtoolbox.core.model.RuleAnalysisResult
import com.selinuxtoolbox.core.model.RuleClassification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanupUiState(
    val activeProject: Project? = null,
    val policy: LoadedPolicy? = null,
    val isLoadingPolicy: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isApplying: Boolean = false,
    val analysisProgress: OperationProgress? = null,
    val cleanupResult: CleanupResult? = null,
    val filteredResults: List<RuleAnalysisResult> = emptyList(),
    val selectedFilter: RuleClassification? = null,
    val error: String? = null
)

sealed class CleanupEvent {
    data class Error(val message: String) : CleanupEvent()
    data class AnalysisComplete(val totalRules: Int, val toRemove: Int) : CleanupEvent()
    data class SaveComplete(val actionId: Long, val outputDir: String, val count: Int) : CleanupEvent()
    object PolicyLoaded : CleanupEvent()
}

@HiltViewModel
class CleanupViewModel @Inject constructor(
    private val getLivePolicyUseCase: GetLivePolicyUseCase,
    private val runCleanupUseCase: RunCleanupUseCase,
    private val getActiveProjectUseCase: GetActiveProjectUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanupUiState())
    val uiState: StateFlow<CleanupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CleanupEvent>()
    val events: SharedFlow<CleanupEvent> = _events.asSharedFlow()

    init {
        observeActiveProject()
    }

    private fun observeActiveProject() {
        viewModelScope.launch {
            getActiveProjectUseCase().collect { project ->
                _uiState.update { it.copy(activeProject = project) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 1: Load live policy
    // -------------------------------------------------------------------------

    fun loadPolicy() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPolicy = true, error = null) }
            getLivePolicyUseCase().fold(
                onSuccess = { policy ->
                    _uiState.update {
                        it.copy(policy = policy, isLoadingPolicy = false)
                    }
                    _events.emit(CleanupEvent.PolicyLoaded)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingPolicy = false,
                            error = "Failed to load policy: ${e.message}"
                        )
                    }
                    _events.emit(CleanupEvent.Error(e.message ?: "Policy load failed"))
                }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Step 2: Run analysis (no files touched yet)
    // -------------------------------------------------------------------------

    fun runAnalysis(
        socPrefixes: List<String> = emptyList(),
        romPrefixes: List<String> = emptyList()
    ) {
        val policy = _uiState.value.policy ?: run {
            viewModelScope.launch {
                _events.emit(CleanupEvent.Error("Load policy first"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    cleanupResult = null,
                    filteredResults = emptyList(),
                    error = null
                )
            }

            runCleanupUseCase.analyze(policy, socPrefixes, romPrefixes).collect { phase ->
                when (phase) {
                    is CleanupPhase.Analyzing -> {
                        _uiState.update { it.copy(analysisProgress = phase.progress) }
                    }
                    is CleanupPhase.Complete -> {
                        val result = phase.result
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                cleanupResult = result,
                                filteredResults = result.results,
                                analysisProgress = null
                            )
                        }
                        val toRemove = result.results.count { r ->
                            r.classification == RuleClassification.ORPHANED ||
                            r.classification == RuleClassification.WRONG_SOC ||
                            r.classification == RuleClassification.WRONG_ROM
                        }
                        _events.emit(
                            CleanupEvent.AnalysisComplete(result.totalRules, toRemove)
                        )
                    }
                    is CleanupPhase.Failed -> {
                        _uiState.update {
                            it.copy(isAnalyzing = false, error = phase.reason)
                        }
                        _events.emit(CleanupEvent.Error(phase.reason))
                    }
                    else -> {}
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Filter results by classification
    // -------------------------------------------------------------------------

    fun setFilter(classification: RuleClassification?) {
        val result = _uiState.value.cleanupResult ?: return
        val filtered = if (classification == null) {
            result.results
        } else {
            result.results.filter { it.classification == classification }
        }
        _uiState.update {
            it.copy(selectedFilter = classification, filteredResults = filtered)
        }
    }

    // -------------------------------------------------------------------------
    // Step 3: Apply and save to SD card output dir
    // -------------------------------------------------------------------------

    fun applyCleanup() {
        val project = _uiState.value.activeProject ?: run {
            viewModelScope.launch {
                _events.emit(CleanupEvent.Error("No active project — create one first"))
            }
            return
        }
        val policy = _uiState.value.policy ?: run {
            viewModelScope.launch { _events.emit(CleanupEvent.Error("No policy loaded")) }
            return
        }
        val result = _uiState.value.cleanupResult ?: run {
            viewModelScope.launch { _events.emit(CleanupEvent.Error("Run analysis first")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true) }

            val phase = runCleanupUseCase.applyAndSave(
                projectId = project.id,
                policy = policy,
                result = result,
                projectFolderPath = project.projectFolderPath
            )

            _uiState.update { it.copy(isApplying = false) }

            when (phase) {
                is CleanupPhase.Saved -> {
                    _events.emit(
                        CleanupEvent.SaveComplete(
                            actionId = phase.actionId,
                            outputDir = phase.outputDir,
                            count = phase.filesModified
                        )
                    )
                }
                is CleanupPhase.Failed -> {
                    _events.emit(CleanupEvent.Error(phase.reason))
                }
                else -> {}
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

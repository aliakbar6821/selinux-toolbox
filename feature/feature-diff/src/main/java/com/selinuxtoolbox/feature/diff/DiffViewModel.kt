package com.selinuxtoolbox.feature.diff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.domain.usecase.DiffLine
import com.selinuxtoolbox.core.domain.usecase.DiffPolicyUseCase
import com.selinuxtoolbox.core.domain.usecase.DiffReport
import com.selinuxtoolbox.core.domain.usecase.DiffResult
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.model.ActiveMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Filter tabs ───────────────────────────────────────────────────────────────

enum class DiffFilter(val label: String) {
    ALL("All"),
    TYPES("Types"),
    ATTRIBUTES("Attrs"),
    RULES("Rules"),
    CONTEXTS("Contexts")
}

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class DiffStep {
    object NoProject  : DiffStep()
    object Loading    : DiffStep()
    data class Ready(val report: DiffReport) : DiffStep()
    data class NoOutputs(val workPath: String) : DiffStep()
    data class Error(val reason: String) : DiffStep()
}

data class DiffUiState(
    val step: DiffStep        = DiffStep.NoProject,
    val projectName: String   = "",
    val activeFilter: DiffFilter = DiffFilter.ALL,
    val visibleLines: List<DiffLine> = emptyList()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class DiffViewModel @Inject constructor(
    private val getActiveProject: GetActiveProjectUseCase,
    private val diffPolicyUseCase: DiffPolicyUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiffUiState())
    val uiState: StateFlow<DiffUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadDiff() }
    }

    private suspend fun loadDiff() {
        _uiState.update { it.copy(step = DiffStep.Loading) }

        val project = getActiveProject().first() ?: run {
            _uiState.update { it.copy(step = DiffStep.NoProject) }
            return
        }

        val aospPath = project.aospPath.ifEmpty { project.projectFolderPath }
        val workPath = project.workPath.ifEmpty { project.projectFolderPath }

        val result = diffPolicyUseCase(
            aospPath = aospPath,
            workPath = workPath,
            mode     = project.activeMode
        )

        when (result) {
            is DiffResult.Success -> {
                _uiState.update {
                    it.copy(
                        step        = DiffStep.Ready(result.report),
                        projectName = project.name,
                        activeFilter = DiffFilter.ALL,
                        visibleLines = allLines(result.report)
                    )
                }
            }
            is DiffResult.NoOutputs ->
                _uiState.update {
                    it.copy(
                        step        = DiffStep.NoOutputs(result.workPath),
                        projectName = project.name
                    )
                }
            is DiffResult.SetupError ->
                _uiState.update { it.copy(step = DiffStep.Error(result.reason)) }
        }
    }

    fun setFilter(filter: DiffFilter) {
        val step = _uiState.value.step as? DiffStep.Ready ?: return
        val lines = when (filter) {
            DiffFilter.ALL       -> allLines(step.report)
            DiffFilter.TYPES     -> step.report.addedTypes
            DiffFilter.ATTRIBUTES-> step.report.addedAttributes + step.report.addedAttributeSets
            DiffFilter.RULES     -> step.report.addedRules
            DiffFilter.CONTEXTS  -> step.report.addedContextEntries
        }
        _uiState.update { it.copy(activeFilter = filter, visibleLines = lines) }
    }

    fun refresh() {
        viewModelScope.launch { loadDiff() }
    }

    private fun allLines(report: DiffReport): List<DiffLine> =
        report.addedTypes +
        report.addedAttributes +
        report.addedAttributeSets +
        report.addedRules +
        report.addedContextEntries
}

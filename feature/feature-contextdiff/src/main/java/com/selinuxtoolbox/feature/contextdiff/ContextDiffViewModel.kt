package com.selinuxtoolbox.feature.contextdiff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.usecase.FullComparisonUseCase
import com.selinuxtoolbox.core.domain.usecase.GenerateMissingContextsUseCase
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.model.ContextFileType
import com.selinuxtoolbox.core.model.Partition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ContextDiffEntry(
    val id: String,
    val pattern: String,
    val context: String,
    val type: String,
    val fileType: ContextFileType,
    val partition: String,
    val oemSourceFile: String
)

sealed class ContextDiffStep {
    object NoProject : ContextDiffStep()
    object Idle : ContextDiffStep()
    object Loading : ContextDiffStep()
    data class Results(val missingEntries: List<ContextDiffEntry>) : ContextDiffStep()
    object Generating : ContextDiffStep()
    data class Done(val outputDir: String) : ContextDiffStep()
    data class Error(val message: String) : ContextDiffStep()
}

data class ContextDiffUiState(
    val step: ContextDiffStep = ContextDiffStep.NoProject,
    val projectName: String = "",
    val acceptedIds: Set<String> = emptySet(),
    val oemPath: String = "",
    val aospPath: String = "",
    val workPath: String = ""
)

sealed class ContextDiffEvent {
    data class Error(val message: String) : ContextDiffEvent()
    data class OutputReady(val outputDir: String) : ContextDiffEvent()
}

@HiltViewModel
class ContextDiffViewModel @Inject constructor(
    private val getActiveProject: GetActiveProjectUseCase,
    private val fullComparisonUseCase: FullComparisonUseCase,
    private val generateMissingContextsUseCase: GenerateMissingContextsUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContextDiffUiState())
    val uiState: StateFlow<ContextDiffUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ContextDiffEvent>()
    val events: SharedFlow<ContextDiffEvent> = _events.asSharedFlow()

    private val outputPath: StateFlow<String> = appPreferences.outputFolderPath
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "/sdcard/SELinuxToolbox"
        )

    init {
        viewModelScope.launch { loadProject() }
    }

    private suspend fun loadProject() {
        val project = getActiveProject().first() ?: run {
            _uiState.update { it.copy(step = ContextDiffStep.NoProject) }
            return
        }
        val resolvedOem = project.oemPath.ifEmpty { "${project.projectFolderPath}/OEM" }
        val resolvedAosp = project.aospPath.ifEmpty { "${project.projectFolderPath}/AOSP" }
        val resolvedWork = project.workPath.ifEmpty { "${project.projectFolderPath}/work" }

        _uiState.update {
            it.copy(
                step = ContextDiffStep.Idle,
                projectName = project.name,
                oemPath = resolvedOem,
                aospPath = resolvedAosp,
                workPath = resolvedWork
            )
        }
    }

    fun runComparison() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(step = ContextDiffStep.Loading) }
            try {
                val result = fullComparisonUseCase(
                    oemPath = state.oemPath,
                    aospPath = state.aospPath,
                    workPath = state.workPath
                )
                if (result is FullComparisonResult.SetupError) {
                    _uiState.update { it.copy(step = ContextDiffStep.Error(result.reason)) }
                    return@launch
                }
                val success = result as FullComparisonResult.Success
                val missingEntries = success.report.missingContextEntries.map { entry ->
                    ContextDiffEntry(
                        id = UUID.randomUUID().toString(),
                        pattern = entry.pattern,
                        context = entry.context,
                        type = entry.type,
                        fileType = ContextFileType.valueOf(entry.fileType),
                        partition = entry.partition,
                        oemSourceFile = entry.oemSourceFile
                    )
                }
                _uiState.update {
                    it.copy(
                        step = ContextDiffStep.Results(missingEntries),
                        acceptedIds = emptySet()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(step = ContextDiffStep.Error(e.message ?: "Comparison failed")) }
                _events.emit(ContextDiffEvent.Error(e.message ?: "Comparison failed"))
            }
        }
    }

    fun toggleAccept(entryId: String) {
        val current = _uiState.value.acceptedIds
        _uiState.update {
            it.copy(
                acceptedIds = if (entryId in current) current - entryId else current + entryId
            )
        }
    }

    fun generateOutput() {
        val state = _uiState.value
        val step = state.step as? ContextDiffStep.Results ?: return
        val acceptedEntries = step.missingEntries.filter { it.id in state.acceptedIds }
        if (acceptedEntries.isEmpty()) {
            viewModelScope.launch {
                _events.emit(ContextDiffEvent.Error("No entries accepted"))
            }
            return
        }

        val acceptedTypes = acceptedEntries.map { it.type }.distinct()

        viewModelScope.launch {
            _uiState.update { it.copy(step = ContextDiffStep.Generating) }
            val result = generateMissingContextsUseCase(
                oemPath = state.oemPath,
                aospPath = state.aospPath,
                workPath = state.workPath,
                acceptedTypes = acceptedTypes.toSet()
            )
            result.fold(
                onSuccess = { outDir ->
                    _uiState.update { it.copy(step = ContextDiffStep.Done(outDir.absolutePath)) }
                    _events.emit(ContextDiffEvent.OutputReady(outDir.absolutePath))
                },
                onFailure = { e ->
                    _uiState.update { it.copy(step = ContextDiffStep.Error(e.message ?: "Generation failed")) }
                    _events.emit(ContextDiffEvent.Error(e.message ?: "Generation failed"))
                }
            )
        }
    }

    fun reset() {
        viewModelScope.launch { loadProject() }
    }
}

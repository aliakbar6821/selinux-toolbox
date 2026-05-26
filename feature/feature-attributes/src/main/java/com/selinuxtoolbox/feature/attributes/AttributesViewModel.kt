package com.selinuxtoolbox.feature.attributes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.usecase.FixAttributesResult
import com.selinuxtoolbox.core.domain.usecase.FixMissingAttributesUseCase
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.model.ActiveMode
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
import javax.inject.Inject

sealed class AttributesStep {
    object NoProject : AttributesStep()
    object Idle : AttributesStep()
    object Scanning : AttributesStep()
    data class Results(
        val missingCount: Int,
        val outputDir: String? = null,
        val allCilFile: String? = null,
        val partitionFiles: Map<String, String> = emptyMap()
    ) : AttributesStep()
    object Generating : AttributesStep()
    data class Done(val outputDir: String) : AttributesStep()
    data class Error(val message: String) : AttributesStep()
}

data class AttributesUiState(
    val step: AttributesStep = AttributesStep.NoProject,
    val projectName: String = "",
    val workPath: String = "",
    val aospPath: String = "",
    val mode: ActiveMode = ActiveMode.OFFLINE
)

sealed class AttributesEvent {
    data class Error(val message: String) : AttributesEvent()
    data class OutputReady(val outputDir: String) : AttributesEvent()
}

@HiltViewModel
class AttributesViewModel @Inject constructor(
    private val getActiveProject: GetActiveProjectUseCase,
    private val fixMissingAttributesUseCase: FixMissingAttributesUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttributesUiState())
    val uiState: StateFlow<AttributesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AttributesEvent>()
    val events: SharedFlow<AttributesEvent> = _events.asSharedFlow()

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
            _uiState.update { it.copy(step = AttributesStep.NoProject) }
            return
        }
        val resolvedAosp = project.aospPath.ifEmpty { project.projectFolderPath }
        val resolvedWork = project.workPath.ifEmpty { project.projectFolderPath }

        _uiState.update {
            it.copy(
                step = AttributesStep.Idle,
                projectName = project.name,
                aospPath = resolvedAosp,
                workPath = resolvedWork,
                mode = project.activeMode
            )
        }
    }

    fun scanAndFix() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(step = AttributesStep.Scanning) }

            val result = fixMissingAttributesUseCase(
                workPath = state.workPath,
                aospPath = state.aospPath,
                mode = state.mode
            )

            when (result) {
                is FixAttributesResult.Success -> {
                    val partitionFiles = result.partitionFiles.mapValues { it.value.absolutePath }
                    _uiState.update {
                        it.copy(
                            step = AttributesStep.Results(
                                missingCount = result.missingCount,
                                outputDir = result.outputDir,
                                allCilFile = result.allCilFile.absolutePath,
                                partitionFiles = partitionFiles
                            )
                        )
                    }
                    _events.emit(AttributesEvent.OutputReady(result.outputDir))
                }
                is FixAttributesResult.NoOutputsFound -> {
                    _uiState.update {
                        it.copy(
                            step = AttributesStep.Error("No generated CIL files found in work/outputs/")
                        )
                    }
                }
                is FixAttributesResult.SetupError -> {
                    _uiState.update {
                        it.copy(
                            step = AttributesStep.Error(result.reason)
                        )
                    }
                    _events.emit(AttributesEvent.Error(result.reason))
                }
            }
        }
    }

    fun reset() {
        viewModelScope.launch { loadProject() }
    }
}

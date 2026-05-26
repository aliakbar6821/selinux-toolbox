package com.selinuxtoolbox.feature.conflicts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.usecase.ConflictReport
import com.selinuxtoolbox.core.domain.usecase.DetectConflictsUseCase
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.model.Project  // ✅ This was missing
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

data class ConflictsUiState(
    val report: ConflictReport? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val projectName: String = ""
)

sealed class ConflictsEvent {
    data class Error(val message: String) : ConflictsEvent()
}

@HiltViewModel
class ConflictsViewModel @Inject constructor(
    private val detectConflictsUseCase: DetectConflictsUseCase,
    private val getActiveProjectUseCase: GetActiveProjectUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConflictsUiState())
    val uiState: StateFlow<ConflictsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConflictsEvent>()
    val events: SharedFlow<ConflictsEvent> = _events.asSharedFlow()

    private val outputPath: StateFlow<String> = appPreferences.outputFolderPath.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "/sdcard/SELinuxToolbox"
    )

    init {
        viewModelScope.launch {
            val project = getActiveProjectUseCase().first()
            project?.let {
                _uiState.update { it.copy(projectName = it.name) }
            }
        }
        detectConflicts()
    }

    fun detectConflicts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val basePath = outputPath.value
                val oemPath = "$basePath/OEM"
                val aospPath = "$basePath/AOSP"
                val workPath = "$basePath/work"

                val report = detectConflictsUseCase(
                    oemPath = oemPath,
                    aospPath = aospPath,
                    workPath = workPath,
                    mappingVersion = "34.0"
                )
                _uiState.update {
                    it.copy(
                        report = report,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
                _events.emit(ConflictsEvent.Error("Failed to detect conflicts: ${e.message}"))
            }
        }
    }
}

package com.selinuxtoolbox.feature.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.analyzer.RcScanResult
import com.selinuxtoolbox.core.domain.analyzer.SeclabelEntry
import com.selinuxtoolbox.core.domain.analyzer.TypeSafety
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.RunRcSeclabelScanUseCase
import com.selinuxtoolbox.core.model.ActiveMode
import com.selinuxtoolbox.core.model.Project
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

sealed class RcScanPhase {
    object Idle : RcScanPhase()
    object Scanning : RcScanPhase()
    data class Results(val result: RcScanResult) : RcScanPhase()
    object Generating : RcScanPhase()
    data class Done(val outputDir: String) : RcScanPhase()
    data class Error(val message: String) : RcScanPhase()
}

data class RcScanUiState(
    val phase: RcScanPhase = RcScanPhase.Idle,
    val activeProject: Project? = null,
    val confirmedDomains: Map<String, Boolean> = emptyMap(),
    val filterText: String = ""
)

sealed class RcScanEvent {
    data class OutputReady(val outputDir: String) : RcScanEvent()
    data class Error(val message: String) : RcScanEvent()
}

@HiltViewModel
class RcScanViewModel @Inject constructor(
    private val runRcSeclabelScanUseCase: RunRcSeclabelScanUseCase,
    private val getActiveProjectUseCase: GetActiveProjectUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(RcScanUiState())
    val uiState: StateFlow<RcScanUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RcScanEvent>()
    val events: SharedFlow<RcScanEvent> = _events.asSharedFlow()

    val outputPath: StateFlow<String> = appPreferences.outputFolderPath
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "/sdcard/SELinuxToolbox"
        )

    init { observeActiveProject() }

    private fun observeActiveProject() {
        viewModelScope.launch {
            getActiveProjectUseCase().collect { project ->
                _uiState.update { it.copy(activeProject = project) }
            }
        }
    }

    // ── Phase 1: Scan ─────────────────────────────────────────────────────────

    fun startScan() {
        val project = _uiState.value.activeProject
        if (project == null) {
            viewModelScope.launch {
                _events.emit(RcScanEvent.Error("No active project. Set an active project first."))
            }
            return
        }

        val oemPath  = project.oemPath.ifBlank  { "${project.projectFolderPath}/OEM"  }
        val aospPath = project.aospPath.ifBlank { "${project.projectFolderPath}/AOSP" }

        viewModelScope.launch {
            _uiState.update { it.copy(phase = RcScanPhase.Scanning) }

            runRcSeclabelScanUseCase.scan(
                oemPath  = oemPath,
                aospPath = aospPath,
                mode     = ActiveMode.OFFLINE
            ).fold(
                onSuccess = { scanResult ->
                    val confirmed = scanResult.missingEntries.associate { entry ->
                        entry.domainName to (entry.safety == TypeSafety.SAFE)
                    }
                    _uiState.update {
                        it.copy(
                            phase            = RcScanPhase.Results(scanResult),
                            confirmedDomains = confirmed
                        )
                    }
                },
                onFailure = { e ->
                    val msg = e.message ?: "Scan failed"
                    _uiState.update { it.copy(phase = RcScanPhase.Error(msg)) }
                    _events.emit(RcScanEvent.Error(msg))
                }
            )
        }
    }

    // ── Confirmation toggles ──────────────────────────────────────────────────

    fun toggleConfirm(domainName: String) {
        _uiState.update { state ->
            val current = state.confirmedDomains[domainName] ?: false
            state.copy(confirmedDomains = state.confirmedDomains + (domainName to !current))
        }
    }

    fun confirmAll(entries: List<SeclabelEntry>) {
        _uiState.update { state ->
            state.copy(confirmedDomains = state.confirmedDomains +
                entries.associate { it.domainName to true })
        }
    }

    fun confirmNone(entries: List<SeclabelEntry>) {
        _uiState.update { state ->
            state.copy(confirmedDomains = state.confirmedDomains +
                entries.associate { it.domainName to false })
        }
    }

    fun onFilterChange(text: String) {
        _uiState.update { it.copy(filterText = text) }
    }

    // ── Phase 2: Generate ─────────────────────────────────────────────────────

    fun generateOutput() {
        val phase = _uiState.value.phase
        if (phase !is RcScanPhase.Results) return

        val confirmed  = _uiState.value.confirmedDomains
        val toGenerate = phase.result.missingEntries.filter {
            confirmed[it.domainName] == true && it.safety != TypeSafety.UNSAFE
        }

        if (toGenerate.isEmpty()) {
            viewModelScope.launch {
                _events.emit(RcScanEvent.Error("No entries selected for generation"))
            }
            return
        }

        val project  = _uiState.value.activeProject ?: return
        val workPath = project.workPath.ifBlank { "${project.projectFolderPath}/work" }

        viewModelScope.launch {
            _uiState.update { it.copy(phase = RcScanPhase.Generating) }

            runRcSeclabelScanUseCase.generateAndWrite(
                confirmedEntries = toGenerate,
                workPath         = workPath
            ).fold(
                onSuccess = { outDir ->
                    _uiState.update { it.copy(phase = RcScanPhase.Done(outDir.absolutePath)) }
                    _events.emit(RcScanEvent.OutputReady(outDir.absolutePath))
                },
                onFailure = { e ->
                    val msg = e.message ?: "Generation failed"
                    _uiState.update { it.copy(phase = RcScanPhase.Error(msg)) }
                    _events.emit(RcScanEvent.Error(msg))
                }
            )
        }
    }

    fun reset() {
        _uiState.update {
            it.copy(phase = RcScanPhase.Idle, confirmedDomains = emptyMap(), filterText = "")
        }
    }
}

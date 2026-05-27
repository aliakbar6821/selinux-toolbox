package com.selinuxtoolbox.feature.compile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.binary.BinaryManager
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.usecase.CompileResult
import com.selinuxtoolbox.core.domain.usecase.CompilePolicyUseCase
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.PreCheckResult
import com.selinuxtoolbox.core.domain.usecase.SecilcError
import com.selinuxtoolbox.core.model.ActiveMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class CompileStep {
    object NoProject    : CompileStep()
    object Initializing : CompileStep()
    object Idle         : CompileStep()
    object DryRunning   : CompileStep()
    object Compiling    : CompileStep()
    data class Success(
        val outputPath: String,
        val sizeKb: Long,
        val filesCompiled: Int
    ) : CompileStep()
    data class DryRunFailed(
        val errors: List<SecilcError>,
        val preChecks: List<PreCheckResult>
    ) : CompileStep()
    data class CompileFailed(val stderr: List<String>) : CompileStep()
    data class SetupError(val reason: String) : CompileStep()
}

data class CompileUiState(
    val step: CompileStep = CompileStep.Initializing,
    val projectName: String = "",
    val aospPath: String = "",
    val workPath: String = "",
    val mode: ActiveMode = ActiveMode.OFFLINE,
    val outputDir: String = "",          // from AppPreferences
    val mappingVersion: String = "34.0", // from project
    val policyVersion: Int = 34
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class CompileViewModel @Inject constructor(
    private val getActiveProject: GetActiveProjectUseCase,
    private val compilePolicyUseCase: CompilePolicyUseCase,
    private val binaryManager: BinaryManager,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompileUiState())
    val uiState: StateFlow<CompileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadProject() }
    }

    private suspend fun loadProject() {
        val outputDir = appPreferences.outputFolderPath.first()
        val project   = getActiveProject().first() ?: run {
            _uiState.update { it.copy(step = CompileStep.NoProject, outputDir = outputDir) }
            return
        }

        val mappingVersion = project.mappingVersion
        val defaultVersion = runCatching { mappingVersion.substringBefore('.').toInt() }.getOrElse { 34 }

        _uiState.update {
            it.copy(
                step           = CompileStep.Idle,
                projectName    = project.name,
                aospPath       = project.aospPath.ifEmpty { project.projectFolderPath },
                workPath       = project.workPath.ifEmpty { project.projectFolderPath },
                mode           = project.activeMode,
                outputDir      = outputDir,
                mappingVersion = mappingVersion,
                policyVersion  = defaultVersion
            )
        }

        // Check binary readiness
        if (!binaryManager.isSecilcReady()) {
            _uiState.update { it.copy(step = CompileStep.Initializing) }
            // Trigger binary initialization in the background
            viewModelScope.launch {
                binaryManager.initialize()
                _uiState.update {
                    it.copy(
                        step = if (binaryManager.isSecilcReady()) CompileStep.Idle else CompileStep.SetupError("Binary still not ready after init")
                    )
                }
            }
        }
    }

    fun retryBinaryInit() {
        viewModelScope.launch {
            _uiState.update { it.copy(step = CompileStep.Initializing) }
            binaryManager.initialize()
            _uiState.update {
                it.copy(
                    step = if (binaryManager.isSecilcReady()) CompileStep.Idle else CompileStep.SetupError("Binary still not ready. Check app/src/main/assets/bin/")
                )
            }
        }
    }

    fun setPolicyVersion(version: Int) {
        _uiState.update { it.copy(policyVersion = version) }
    }

    fun compile() {
        val state = _uiState.value
        viewModelScope.launch {
            // Phase 1 — dry-run indicator
            _uiState.update { it.copy(step = CompileStep.DryRunning) }

            val outputPath = "${state.outputDir}/precompiled_sepolicy"
            val result     = compilePolicyUseCase(
                aospPath      = state.aospPath,
                workPath      = state.workPath,
                outputPath    = outputPath,
                mode          = state.mode,
                policyVersion = state.policyVersion
            )

            // Phase 2 — if dry-run passed, compilePolicyUseCase switches to real compile internally
            val nextStep = when (result) {
                is CompileResult.Success -> CompileStep.Success(
                    outputPath    = result.outputPath,
                    sizeKb        = result.sizeBytes / 1024,
                    filesCompiled = result.filesCompiled
                )
                is CompileResult.DryRunFailed -> CompileStep.DryRunFailed(
                    errors    = result.errors,
                    preChecks = result.preChecks
                )
                is CompileResult.CompileFailed -> CompileStep.CompileFailed(result.stderr)
                is CompileResult.SetupError    -> CompileStep.SetupError(result.reason)
            }
            _uiState.update { it.copy(step = nextStep) }
        }
    }

    fun reset() {
        viewModelScope.launch { loadProject() }
    }
}

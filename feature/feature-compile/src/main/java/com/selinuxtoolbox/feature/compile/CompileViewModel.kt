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
import kotlinx.coroutines.delay
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
    val policyVersion: Int = 34,
    val initAttempts: Int = 0
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
        checkBinaryReadiness()
    }

    private fun checkBinaryReadiness() {
        if (!binaryManager.isSecilcReady()) {
            _uiState.update { it.copy(step = CompileStep.Initializing) }
            viewModelScope.launch {
                // Try to initialize with a timeout
                var attempts = 0
                var success = false
                while (attempts < 3 && !success) {
                    attempts++
                    _uiState.update { it.copy(initAttempts = attempts) }
                    val result = binaryManager.initialize()
                    if (result.isSuccess && binaryManager.isSecilcReady()) {
                        success = true
                        _uiState.update { 
                            it.copy(
                                step = CompileStep.Idle,
                                initAttempts = 0
                            )
                        }
                    } else {
                        delay(500)
                    }
                }
                if (!success) {
                    _uiState.update {
                        it.copy(
                            step = CompileStep.SetupError(
                                "Binary initialization failed after 3 attempts.\n\n" +
                                "Please ensure:\n" +
                                "1. ARM64 'secilc' and 'sepolicy-analyze' binaries are in app/src/main/assets/bin/\n" +
                                "2. The binaries are executable (chmod +x)\n" +
                                "3. The app has storage permission to write to private dir"
                            )
                        )
                    }
                }
            }
        }
    }

    fun retryBinaryInit() {
        _uiState.update { it.copy(step = CompileStep.Initializing, initAttempts = 0) }
        checkBinaryReadiness()
    }

    fun setPolicyVersion(version: Int) {
        _uiState.update { it.copy(policyVersion = version) }
    }

    fun compile() {
        val state = _uiState.value
        if (!binaryManager.isSecilcReady()) {
            _uiState.update {
                it.copy(
                    step = CompileStep.SetupError(
                        "secilc binary not ready.\n\n" +
                        "Tap 'Retry Binary Initialization' on the previous screen."
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(step = CompileStep.DryRunning) }

            val outputPath = "${state.outputDir}/precompiled_sepolicy"
            val result     = compilePolicyUseCase(
                aospPath      = state.aospPath,
                workPath      = state.workPath,
                outputPath    = outputPath,
                mode          = state.mode,
                policyVersion = state.policyVersion
            )

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

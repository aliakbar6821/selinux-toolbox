package com.selinuxtoolbox.feature.denials

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.domain.usecase.AnalyzeDenialsUseCase
import com.selinuxtoolbox.core.domain.usecase.ApplyDenialFixesUseCase
import com.selinuxtoolbox.core.domain.usecase.ApplyFixResult
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.ImportLogResult
import com.selinuxtoolbox.core.domain.usecase.ImportLogUseCase
import com.selinuxtoolbox.core.model.ActiveMode
import com.selinuxtoolbox.core.model.DenialGroup
import com.selinuxtoolbox.core.model.FixSuggestion
import com.selinuxtoolbox.core.domain.analyzer.TypeSafety
import com.selinuxtoolbox.core.domain.analyzer.SafetyConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class DenialsStep {
    object NoProject    : DenialsStep()
    object Idle         : DenialsStep()   // project loaded, no log imported yet
    object Importing    : DenialsStep()
    object Analyzing    : DenialsStep()
    data class Results(
        val groups: List<DenialGroup>,
        val acceptedIds: Set<String>,     // groupKey of accepted groups
        val totalDenials: Int,
        val safeCount: Int,
        val reviewCount: Int,
        val unsafeCount: Int,
        val intentionalCount: Int,
        val logFileName: String
    ) : DenialsStep()
    object Applying     : DenialsStep()
    data class Done(
        val outputPath: String,
        val rulesWritten: Int
    ) : DenialsStep()
    data class Error(val message: String) : DenialsStep()
}

data class DenialsUiState(
    val step: DenialsStep = DenialsStep.NoProject,
    val projectName: String = "",
    val aospPath: String = "",
    val workPath: String = "",
    val projectId: Long = -1L,
    val mode: ActiveMode = ActiveMode.OFFLINE
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class DenialsViewModel @Inject constructor(
    application: Application,
    private val getActiveProject: GetActiveProjectUseCase,
    private val importLog: ImportLogUseCase,
    private val analyzeDenials: AnalyzeDenialsUseCase,
    private val applyFixes: ApplyDenialFixesUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DenialsUiState())
    val uiState: StateFlow<DenialsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadProject() }
    }

    private suspend fun loadProject() {
        val project = getActiveProject() ?: run {
            _uiState.update { it.copy(step = DenialsStep.NoProject) }
            return
        }
        _uiState.update {
            it.copy(
                step        = DenialsStep.Idle,
                projectName = project.name,
                aospPath    = project.aospPath,
                workPath    = project.workPath,
                projectId   = project.id,
                mode        = project.activeMode
            )
        }
    }

    // Called when user picks a log file via SAF picker
    fun onLogFilePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(step = DenialsStep.Importing) }

            val state = _uiState.value
            // Resolve Uri to a temp File the use case can read
            val tempFile = uriToTempFile(uri) ?: run {
                _uiState.update { it.copy(step = DenialsStep.Error("Cannot read selected file")) }
                return@launch
            }

            val importResult = importLog(
                projectId  = state.projectId,
                workPath   = state.workPath,
                sourceFile = tempFile
            )

            when (importResult) {
                is ImportLogResult.Failure -> {
                    _uiState.update { it.copy(step = DenialsStep.Error(importResult.reason)) }
                }
                is ImportLogResult.Success -> {
                    _uiState.update { it.copy(step = DenialsStep.Analyzing) }
                    runAnalysis(
                        logFilePath = importResult.log.filePath,
                        fileName    = importResult.log.fileName,
                        state       = state
                    )
                }
            }

            tempFile.delete()
        }
    }

    private suspend fun runAnalysis(logFilePath: String, fileName: String, state: DenialsUiState) {
        val result = analyzeDenials(
            logFilePath = logFilePath,
            aospPath    = state.aospPath,
            mode        = state.mode
        )

        // Auto-accept all SAFE groups that are not intentional
        val autoAccepted = result.groups
            .filter { !it.isIntentional && SafetyConfig.classify(it.sourceDomain) == TypeSafety.SAFE }
            .map { groupKey(it) }
            .toSet()

        _uiState.update {
            it.copy(
                step = DenialsStep.Results(
                    groups           = result.groups,
                    acceptedIds      = autoAccepted,
                    totalDenials     = result.totalDenials,
                    safeCount        = result.safeCount,
                    reviewCount      = result.reviewCount,
                    unsafeCount      = result.unsafeCount,
                    intentionalCount = result.intentionalCount,
                    logFileName      = fileName
                )
            )
        }
    }

    fun toggleGroupAccepted(group: DenialGroup) {
        val state = _uiState.value
        val results = state.step as? DenialsStep.Results ?: return
        val key = groupKey(group)

        // Cannot accept unsafe or intentional groups
        if (group.isIntentional) return
        if (SafetyConfig.classify(group.sourceDomain) == TypeSafety.UNSAFE) return

        val newAccepted = if (key in results.acceptedIds) {
            results.acceptedIds - key
        } else {
            results.acceptedIds + key
        }
        _uiState.update { it.copy(step = results.copy(acceptedIds = newAccepted)) }
    }

    fun applyAcceptedFixes() {
        val state = _uiState.value
        val results = state.step as? DenialsStep.Results ?: return

        val acceptedGroups = results.groups.filter { groupKey(it) in results.acceptedIds }
        if (acceptedGroups.isEmpty()) {
            _uiState.update { it.copy(step = DenialsStep.Error("No groups accepted — nothing to apply")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(step = DenialsStep.Applying) }

            when (val result = applyFixes(
                projectId      = state.projectId,
                workPath       = state.workPath,
                aospPath       = state.aospPath,
                mode           = state.mode,
                acceptedGroups = acceptedGroups
            )) {
                is ApplyFixResult.Success -> _uiState.update {
                    it.copy(step = DenialsStep.Done(result.outputFolderPath, result.rulesWritten))
                }
                is ApplyFixResult.Failure -> _uiState.update {
                    it.copy(step = DenialsStep.Error(result.reason))
                }
            }
        }
    }

    fun reset() {
        viewModelScope.launch { loadProject() }
    }

    private fun groupKey(g: DenialGroup) = "${g.sourceDomain}::${g.targetType}::${g.objectClass}"

    private fun uriToTempFile(uri: Uri): File? {
        return try {
            val ctx = getApplication<Application>()
            val stream = ctx.contentResolver.openInputStream(uri) ?: return null
            val name   = uri.lastPathSegment ?: "imported_log.txt"
            val tmp    = File(ctx.cacheDir, "log_import_$name")
            tmp.outputStream().use { out -> stream.copyTo(out) }
            stream.close()
            tmp
        } catch (e: Exception) { null }
    }
}

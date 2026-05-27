package com.selinuxtoolbox.feature.logimporter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.ImportLogResult
import com.selinuxtoolbox.core.domain.usecase.ImportLogUseCase
import com.selinuxtoolbox.core.model.ImportedLog
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
import java.io.File
import javax.inject.Inject

data class LogImporterUiState(
    val importedLogs: List<ImportedLog> = emptyList(),
    val projectName: String = "",
    val workPath: String = "",
    val projectId: Long = -1L,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null
)

sealed class LogImporterEvent {
    data class ImportSuccess(val log: ImportedLog) : LogImporterEvent()
    data class Error(val message: String) : LogImporterEvent()
}

@HiltViewModel
class LogImporterViewModel @Inject constructor(
    private val getActiveProject: GetActiveProjectUseCase,
    private val importLogUseCase: ImportLogUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogImporterUiState())
    val uiState: StateFlow<LogImporterUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LogImporterEvent>()
    val events: SharedFlow<LogImporterEvent> = _events.asSharedFlow()

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
            _uiState.update { it.copy(projectName = "No active project") }
            return
        }
        val resolvedWork = project.workPath.ifEmpty { "${project.projectFolderPath}/work" }
        _uiState.update {
            it.copy(
                projectName = project.name,
                workPath = resolvedWork,
                projectId = project.id
            )
        }
        loadImportedLogs()
    }

    private fun loadImportedLogs() {
        viewModelScope.launch {
            val logsDir = File(_uiState.value.workPath, "imported_logs")
            if (!logsDir.exists()) {
                _uiState.update { it.copy(importedLogs = emptyList()) }
                return@launch
            }
            val logs = logsDir.listFiles()
                ?.filter { it.isFile && it.extension in listOf("txt", "log") }
                ?.map { file ->
                    ImportedLog(
                        id = 0L,
                        projectId = _uiState.value.projectId,
                        fileName = file.name,
                        filePath = file.absolutePath,
                        importedAt = file.lastModified(),
                        logType = com.selinuxtoolbox.core.model.LogType.UNKNOWN,
                        totalLines = file.readLines().size,
                        avcDenialCount = file.readLines().count { it.contains("avc:") && it.contains("denied") },
                        unmappedContextCount = 0,
                        undefinedTypeCount = 0
                    )
                }
                ?.sortedByDescending { it.importedAt }
                ?: emptyList()
            _uiState.update { it.copy(importedLogs = logs) }
        }
    }

    fun importLog(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            try {
                val state = _uiState.value
                val ctx = getApplication<android.app.Application>()
                val stream = ctx.contentResolver.openInputStream(uri) ?: run {
                    _events.emit(LogImporterEvent.Error("Cannot open file"))
                    _uiState.update { it.copy(isImporting = false) }
                    return@launch
                }
                val tempFile = File(ctx.cacheDir, "import_${System.currentTimeMillis()}.txt")
                tempFile.outputStream().use { out -> stream.copyTo(out) }
                stream.close()

                val result = importLogUseCase(
                    projectId = state.projectId,
                    workPath = state.workPath,
                    sourceFile = tempFile
                )
                tempFile.delete()

                when (result) {
                    is ImportLogResult.Success -> {
                        _events.emit(LogImporterEvent.ImportSuccess(result.log))
                        loadImportedLogs()
                    }
                    is ImportLogResult.Failure -> {
                        _events.emit(LogImporterEvent.Error(result.reason))
                        _uiState.update { it.copy(error = result.reason) }
                    }
                }
            } catch (e: Exception) {
                _events.emit(LogImporterEvent.Error("Import failed: ${e.message}"))
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    private fun getApplication(): android.app.Application =
        androidx.lifecycle.AndroidViewModel(getApplication<android.app.Application>()) {
            // dummy call to get context
        }.getApplication()
}

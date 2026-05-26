package com.selinuxtoolbox.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.usecase.AddNoteUseCase
import com.selinuxtoolbox.core.domain.usecase.ArchiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.CreateProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.DeleteProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.ExportProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.GetAllProjectsUseCase
import com.selinuxtoolbox.core.domain.usecase.GetProjectActionsUseCase
import com.selinuxtoolbox.core.domain.usecase.ImportProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.SetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.ValidateActionsUseCase
import com.selinuxtoolbox.core.model.ActionRecord
import com.selinuxtoolbox.core.model.ActionValidation
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ProjectDetailUiState(
    val project: Project? = null,
    val actions: List<ActionRecord> = emptyList(),
    val validations: List<ActionValidation> = emptyList(),
    val isLoadingValidations: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class CreateProjectFormState(
    val name: String = "",
    val sourceDevice: String = "",
    val targetDevice: String = "",
    val sourceRom: String = "",
    val targetRom: String = "",
    val mode: ActiveMode = ActiveMode.OFFLINE,
    val showModeDialog: Boolean = false,
    val nameError: String? = null,
    val isSubmitting: Boolean = false
)

sealed class ProjectsEvent {
    data class ProjectCreated(val projectId: Long) : ProjectsEvent()
    data class ProjectDeleted(val name: String) : ProjectsEvent()
    data class ExportSuccess(val zipPath: String) : ProjectsEvent()
    data class ImportSuccess(val projectId: Long) : ProjectsEvent()
    data class Error(val message: String) : ProjectsEvent()
    data class ActiveProjectSet(val projectId: Long) : ProjectsEvent()
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val getAllProjectsUseCase: GetAllProjectsUseCase,
    private val createProjectUseCase: CreateProjectUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
    private val archiveProjectUseCase: ArchiveProjectUseCase,
    private val setActiveProjectUseCase: SetActiveProjectUseCase,
    private val exportProjectUseCase: ExportProjectUseCase,
    private val importProjectUseCase: ImportProjectUseCase,
    private val getProjectActionsUseCase: GetProjectActionsUseCase,
    private val validateActionsUseCase: ValidateActionsUseCase,
    private val addNoteUseCase: AddNoteUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState     = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(ProjectDetailUiState())
    val detailState: StateFlow<ProjectDetailUiState> = _detailState.asStateFlow()

    private val _formState   = MutableStateFlow(CreateProjectFormState())
    val formState: StateFlow<CreateProjectFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<ProjectsEvent>()
    val events: SharedFlow<ProjectsEvent> = _events.asSharedFlow()

    val outputPath: StateFlow<String> = appPreferences.outputFolderPath
        .stateIn(
            scope    = viewModelScope,
            started  = SharingStarted.WhileSubscribed(5000),
            initialValue = "/sdcard/SELinuxToolbox"
        )

    init { observeProjects() }

    // ── Observe ───────────────────────────────────────────────────────────────

    private fun observeProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getAllProjectsUseCase().collect { projects ->
                _uiState.update { it.copy(projects = projects, isLoading = false, error = null) }
            }
        }
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    fun loadProjectDetail(project: Project) {
        _detailState.update { it.copy(project = project, isLoading = true) }
        viewModelScope.launch {
            getProjectActionsUseCase(project.id).collect { actions ->
                _detailState.update { it.copy(actions = actions, isLoading = false) }
            }
        }
    }

    fun validateProjectActions(projectId: Long) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoadingValidations = true) }
            val validations = validateActionsUseCase(projectId)
            _detailState.update { it.copy(validations = validations, isLoadingValidations = false) }
        }
    }

    // ── Form fields ───────────────────────────────────────────────────────────

    // trimStart prevents leading spaces from sneaking into folder names
    fun onNameChange(value: String) =
        _formState.update { it.copy(name = value.trimStart(), nameError = null) }

    fun onSourceDeviceChange(value: String) =
        _formState.update { it.copy(sourceDevice = value) }

    fun onTargetDeviceChange(value: String) =
        _formState.update { it.copy(targetDevice = value) }

    fun onSourceRomChange(value: String) =
        _formState.update { it.copy(sourceRom = value) }

    fun onTargetRomChange(value: String) =
        _formState.update { it.copy(targetRom = value) }

    fun resetForm() = _formState.update { CreateProjectFormState() }

    // ── Create flow ───────────────────────────────────────────────────────────

    /**
     * Step 1: validate name, then show OFFLINE/LIVE mode dialog.
     * Always trims the name before any check so " ColorOS" → "ColorOS".
     */
    fun onRequestCreate() {
        val trimmedName = _formState.value.name.trim()
        // Write trimmed value back so the field shows it correctly
        _formState.update { it.copy(name = trimmedName) }

        if (trimmedName.isBlank()) {
            _formState.update { it.copy(nameError = "Project name is required") }
            return
        }
        val duplicate = _uiState.value.projects.any {
            it.name.equals(trimmedName, ignoreCase = true)
        }
        if (duplicate) {
            _formState.update { it.copy(nameError = "A project with this name already exists") }
            return
        }
        _formState.update { it.copy(showModeDialog = true, nameError = null) }
    }

    /** Step 2: user picked OFFLINE or LIVE — now actually create. */
    fun onModeSelected(mode: ActiveMode) {
        _formState.update { it.copy(mode = mode, showModeDialog = false) }
        submitCreateProject()
    }

    fun onModeDismissed() {
        _formState.update { it.copy(showModeDialog = false) }
    }

    private fun submitCreateProject() {
        val form = _formState.value
        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true) }
            val result = createProjectUseCase(
                name           = form.name,        // already trimmed in onRequestCreate
                sourceDevice   = form.sourceDevice.trim(),
                targetDevice   = form.targetDevice.trim(),
                sourceRom      = form.sourceRom.trim(),
                targetRom      = form.targetRom.trim(),
                outputBasePath = outputPath.value,
                mode           = form.mode
            )
            result.fold(
                onSuccess = { projectId ->
                    _formState.update { CreateProjectFormState() }
                    _events.emit(ProjectsEvent.ProjectCreated(projectId))
                },
                onFailure = { e ->
                    _events.emit(ProjectsEvent.Error(e.message ?: "Failed to create project"))
                }
            )
            _formState.update { it.copy(isSubmitting = false) }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun onDeleteProject(project: Project) {
        viewModelScope.launch {
            deleteProjectUseCase(project.id).fold(
                onSuccess = { _events.emit(ProjectsEvent.ProjectDeleted(project.name)) },
                onFailure = { e -> _events.emit(ProjectsEvent.Error(e.message ?: "Failed to delete")) }
            )
        }
    }

    // ── Archive ───────────────────────────────────────────────────────────────

    fun onArchiveProject(projectId: Long) {
        viewModelScope.launch {
            archiveProjectUseCase(projectId).fold(
                onSuccess = {},
                onFailure = { e -> _events.emit(ProjectsEvent.Error(e.message ?: "Failed to archive")) }
            )
        }
    }

    // ── Set active ────────────────────────────────────────────────────────────

    fun onSetActiveProject(projectId: Long) {
        viewModelScope.launch {
            setActiveProjectUseCase(projectId).fold(
                onSuccess = { _events.emit(ProjectsEvent.ActiveProjectSet(projectId)) },
                onFailure = { e -> _events.emit(ProjectsEvent.Error(e.message ?: "Failed to set active")) }
            )
        }
    }

    // ── Export / Import ───────────────────────────────────────────────────────

    fun onExportProject(projectId: Long) {
        viewModelScope.launch {
            exportProjectUseCase(projectId, "${outputPath.value}/exports").fold(
                onSuccess = { zipPath -> _events.emit(ProjectsEvent.ExportSuccess(zipPath)) },
                onFailure = { e -> _events.emit(ProjectsEvent.Error(e.message ?: "Export failed")) }
            )
        }
    }

    fun onImportProject(zipPath: String) {
        viewModelScope.launch {
            importProjectUseCase(zipPath, "${outputPath.value}/projects").fold(
                onSuccess = { projectId -> _events.emit(ProjectsEvent.ImportSuccess(projectId)) },
                onFailure = { e -> _events.emit(ProjectsEvent.Error(e.message ?: "Import failed")) }
            )
        }
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    fun onAddNote(
        projectId: Long,
        content: String,
        tags: List<String> = emptyList(),
        actionId: Long? = null
    ) {
        viewModelScope.launch {
            addNoteUseCase(projectId, content, tags, actionId).fold(
                onSuccess = {},
                onFailure = { e -> _events.emit(ProjectsEvent.Error(e.message ?: "Failed to add note")) }
            )
        }
    }
}

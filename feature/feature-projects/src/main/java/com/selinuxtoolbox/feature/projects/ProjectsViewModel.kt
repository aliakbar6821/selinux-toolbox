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
import com.selinuxtoolbox.core.model.ActionType
import com.selinuxtoolbox.core.model.ActionValidity
import com.selinuxtoolbox.core.model.ActionValidation
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
    // Split into two groups to work around KSP 11-parameter limit issues
    private val getAllProjects: GetAllProjectsUseCase,
    private val createProject: CreateProjectUseCase,
    private val deleteProject: DeleteProjectUseCase,
    private val archiveProject: ArchiveProjectUseCase,
    private val setActiveProject: SetActiveProjectUseCase,
    private val exportProject: ExportProjectUseCase,
    private val importProject: ImportProjectUseCase,
    private val getProjectActions: GetProjectActionsUseCase,
    private val validateActions: ValidateActionsUseCase,
    private val addNote: AddNoteUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(ProjectDetailUiState())
    val detailState: StateFlow<ProjectDetailUiState> = _detailState.asStateFlow()

    private val _formState = MutableStateFlow(CreateProjectFormState())
    val formState: StateFlow<CreateProjectFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<ProjectsEvent>()
    val events: SharedFlow<ProjectsEvent> = _events.asSharedFlow()

    val outputPath: StateFlow<String> = appPreferences.outputFolderPath
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "/sdcard/SELinuxToolbox"
        )

    init {
        observeProjects()
    }

    private fun observeProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getAllProjects().collect { projects ->
                _uiState.update {
                    it.copy(projects = projects, isLoading = false, error = null)
                }
            }
        }
    }

    fun loadProjectDetail(project: Project) {
        _detailState.update { it.copy(project = project, isLoading = true) }
        viewModelScope.launch {
            getProjectActions(project.id).collect { actions ->
                _detailState.update {
                    it.copy(actions = actions, isLoading = false)
                }
            }
        }
    }

    fun validateProjectActions(projectId: Long) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoadingValidations = true) }
            val validations = validateActions(projectId)
            _detailState.update {
                it.copy(validations = validations, isLoadingValidations = false)
            }
        }
    }

    fun onNameChange(value: String) =
        _formState.update { it.copy(name = value, nameError = null) }

    fun onSourceDeviceChange(value: String) =
        _formState.update { it.copy(sourceDevice = value) }

    fun onTargetDeviceChange(value: String) =
        _formState.update { it.copy(targetDevice = value) }

    fun onSourceRomChange(value: String) =
        _formState.update { it.copy(sourceRom = value) }

    fun onTargetRomChange(value: String) =
        _formState.update { it.copy(targetRom = value) }

    fun resetForm() = _formState.update { CreateProjectFormState() }

    fun submitCreateProject() {
        val form = _formState.value
        if (form.name.isBlank()) {
            _formState.update { it.copy(nameError = "Project name is required") }
            return
        }
        val duplicate = _uiState.value.projects.any {
            it.name.equals(form.name.trim(), ignoreCase = true)
        }
        if (duplicate) {
            _formState.update { it.copy(nameError = "A project with this name already exists") }
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true) }
            createProject(
                name = form.name.trim(),
                sourceDevice = form.sourceDevice.trim(),
                targetDevice = form.targetDevice.trim(),
                sourceRom = form.sourceRom.trim(),
                targetRom = form.targetRom.trim(),
                outputBasePath = outputPath.value
            ).fold(
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

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            deleteProject(project.id).fold(
                onSuccess = { _events.emit(ProjectsEvent.ProjectDeleted(project.name)) },
                onFailure = { e ->
                    _events.emit(ProjectsEvent.Error(e.message ?: "Failed to delete project"))
                }
            )
        }
    }

    fun archiveProject(projectId: Long) {
        viewModelScope.launch {
            archiveProject(projectId).fold(
                onSuccess = {},
                onFailure = { e ->
                    _events.emit(ProjectsEvent.Error(e.message ?: "Failed to archive project"))
                }
            )
        }
    }

    fun setActive(projectId: Long) {
        viewModelScope.launch {
            setActiveProject(projectId).fold(
                onSuccess = { _events.emit(ProjectsEvent.ActiveProjectSet(projectId)) },
                onFailure = { e ->
                    _events.emit(ProjectsEvent.Error(e.message ?: "Failed to set active project"))
                }
            )
        }
    }

    fun exportProject(projectId: Long) {
        viewModelScope.launch {
            val exportsDir = "${outputPath.value}/exports"
            exportProject(projectId, exportsDir).fold(
                onSuccess = { zipPath -> _events.emit(ProjectsEvent.ExportSuccess(zipPath)) },
                onFailure = { e ->
                    _events.emit(ProjectsEvent.Error(e.message ?: "Export failed"))
                }
            )
        }
    }

    fun importProject(zipPath: String) {
        viewModelScope.launch {
            val projectsDir = "${outputPath.value}/projects"
            importProject(zipPath, projectsDir).fold(
                onSuccess = { projectId -> _events.emit(ProjectsEvent.ImportSuccess(projectId)) },
                onFailure = { e ->
                    _events.emit(ProjectsEvent.Error(e.message ?: "Import failed"))
                }
            )
        }
    }

    fun addNote(
        projectId: Long,
        content: String,
        tags: List<String> = emptyList(),
        actionId: Long? = null
    ) {
        viewModelScope.launch {
            addNote(projectId, content, tags, actionId).fold(
                onSuccess = {},
                onFailure = { e ->
                    _events.emit(ProjectsEvent.Error(e.message ?: "Failed to add note"))
                }
            )
        }
    }
}

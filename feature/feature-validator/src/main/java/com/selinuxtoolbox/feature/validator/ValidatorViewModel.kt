package com.selinuxtoolbox.feature.validator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.domain.usecase.CilValidationResult
import com.selinuxtoolbox.core.domain.usecase.PreCheckResult
import com.selinuxtoolbox.core.domain.usecase.SecilcError
import com.selinuxtoolbox.core.domain.usecase.SeclabelCheck
import com.selinuxtoolbox.core.domain.usecase.SeclabelStatus
import com.selinuxtoolbox.core.domain.usecase.SeclabelValidationResult
import com.selinuxtoolbox.core.domain.usecase.ValidateCilUseCase
import com.selinuxtoolbox.core.domain.usecase.ValidateSeclabelsUseCase
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.model.ActiveMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class ValidatorStep {
    object NoProject : ValidatorStep()
    object Idle      : ValidatorStep()
    object Running   : ValidatorStep()
    data class CilResult(
        val result: CilValidationResult,
        val filesChecked: Int
    ) : ValidatorStep()
    data class SeclabelResult(
        val result: SeclabelValidationResult
    ) : ValidatorStep()
    data class Error(val message: String) : ValidatorStep()
}

data class ValidatorUiState(
    val step: ValidatorStep = ValidatorStep.NoProject,
    val projectName: String = "",
    val oemPath: String     = "",
    val aospPath: String    = "",
    val workPath: String    = "",
    val mode: ActiveMode    = ActiveMode.OFFLINE
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ValidatorViewModel @Inject constructor(
    private val getActiveProject: GetActiveProjectUseCase,
    private val validateCil: ValidateCilUseCase,
    private val validateSeclabels: ValidateSeclabelsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ValidatorUiState())
    val uiState: StateFlow<ValidatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadProject() }
    }

    private suspend fun loadProject() {
        val project = getActiveProject().first() ?: run {
            _uiState.update { it.copy(step = ValidatorStep.NoProject) }
            return
        }
        val resolvedAosp = if (project.aospPath.isNotEmpty()) project.aospPath
                           else project.projectFolderPath
        val resolvedWork = if (project.workPath.isNotEmpty()) project.workPath
                           else project.projectFolderPath
        val resolvedOem  = if (project.oemPath.isNotEmpty()) project.oemPath
                           else project.projectFolderPath

        _uiState.update {
            it.copy(
                step        = ValidatorStep.Idle,
                projectName = project.name,
                oemPath     = resolvedOem,
                aospPath    = resolvedAosp,
                workPath    = resolvedWork,
                mode        = project.activeMode
            )
        }
    }

    fun runCilValidation() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(step = ValidatorStep.Running) }
            val result = validateCil(
                aospPath = state.aospPath,
                workPath = state.workPath,
                mode     = state.mode
            )
            val filesChecked = when (result) {
                is CilValidationResult.Pass -> result.filesChecked
                is CilValidationResult.Fail -> result.errors.size
                else -> 0
            }
            _uiState.update {
                it.copy(step = ValidatorStep.CilResult(result, filesChecked))
            }
        }
    }

    fun runSeclabelValidation() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(step = ValidatorStep.Running) }
            val result = validateSeclabels(
                oemPath  = state.oemPath,
                aospPath = state.aospPath,
                workPath = state.workPath,
                mode     = state.mode
            )
            _uiState.update {
                it.copy(step = ValidatorStep.SeclabelResult(result))
            }
        }
    }

    fun reset() {
        viewModelScope.launch { loadProject() }
    }
}

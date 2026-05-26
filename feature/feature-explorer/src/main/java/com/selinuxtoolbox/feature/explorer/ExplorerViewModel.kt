package com.selinuxtoolbox.feature.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.usecase.ManualSearchResult
import com.selinuxtoolbox.core.domain.usecase.ManualTypeSearchUseCase
import com.selinuxtoolbox.core.domain.usecase.TypeSearchResult
import com.selinuxtoolbox.core.domain.path.PolicyPartition
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

data class ExplorerUiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResult: ManualSearchResult? = null,
    val selectedPartition: PolicyPartition = PolicyPartition.VENDOR
)

sealed class ExplorerEvent {
    data class Error(val message: String) : ExplorerEvent()
    data class FixGenerated(val outputDir: String) : ExplorerEvent()
}

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val manualTypeSearchUseCase: ManualTypeSearchUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ExplorerEvent>()
    val events: SharedFlow<ExplorerEvent> = _events.asSharedFlow()

    // Convert Flow<String> to StateFlow<String> – provides .value without suspend
    private val outputPath: StateFlow<String> = appPreferences.outputFolderPath.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "/sdcard/SELinuxToolbox"
    )

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResult = null) }
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            // Use .value – no suspend needed
            val outputPath = outputPath.value
            val oemPath = "$outputPath/OEM"
            val aospPath = "$outputPath/AOSP"

            val result = manualTypeSearchUseCase.search(query, oemPath, aospPath)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResult = result
                )
            }

            if (result is ManualSearchResult.Error) {
                _events.emit(ExplorerEvent.Error(result.reason))
            }
        }
    }

    fun generateFix() {
        val result = _uiState.value.searchResult
        if (result !is ManualSearchResult.Found) return

        val typeName = result.result.typeName
        val workPath = outputPath.value

        viewModelScope.launch {
            val generateResult = manualTypeSearchUseCase.generateFix(
                typeName = typeName,
                workPath = workPath,
                partition = _uiState.value.selectedPartition
            )
            when (generateResult) {
                is com.selinuxtoolbox.core.domain.usecase.GenerateFixResult.Success -> {
                    _events.emit(ExplorerEvent.FixGenerated(generateResult.outputDir))
                }
                is com.selinuxtoolbox.core.domain.usecase.GenerateFixResult.SetupError -> {
                    _events.emit(ExplorerEvent.Error(generateResult.reason))
                }
            }
        }
    }
}

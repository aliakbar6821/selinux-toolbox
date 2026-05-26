package com.selinuxtoolbox.feature.contexts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.data.root.RootFileReader
import com.selinuxtoolbox.core.domain.parser.ContextFileParser
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.domain.path.PolicyPartition
import com.selinuxtoolbox.core.model.ActiveMode
import com.selinuxtoolbox.core.model.ContextEntry
import com.selinuxtoolbox.core.model.ContextFileType
import com.selinuxtoolbox.core.model.Partition
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

data class ContextsUiState(
    val oemEntries: List<ContextEntry> = emptyList(),
    val aospEntries: List<ContextEntry> = emptyList(),
    val commonEntries: List<ContextEntry> = emptyList(),
    val selectedPartition: Partition = Partition.SYSTEM,
    val selectedFileType: ContextFileType = ContextFileType.FILE,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class ContextsEvent {
    data class Error(val message: String) : ContextsEvent()
}

@HiltViewModel
class ContextsViewModel @Inject constructor(
    private val rootFileReader: RootFileReader,
    private val contextParser: ContextFileParser,
    private val pathResolver: PathResolver,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContextsUiState())
    val uiState: StateFlow<ContextsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ContextsEvent>()
    val events: SharedFlow<ContextsEvent> = _events.asSharedFlow()

    private val outputPath: StateFlow<String> = appPreferences.outputFolderPath.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "/sdcard/SELinuxToolbox"
    )

    init {
        loadContexts()
    }

    fun loadContexts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val basePath = outputPath.value
                val oemPath = "$basePath/OEM"
                val aospPath = "$basePath/AOSP"
                val partition = _uiState.value.selectedPartition
                val fileType = _uiState.value.selectedFileType
                val query = _uiState.value.searchQuery

                val oemEntries = loadEntries(oemPath, partition, fileType)
                val aospEntries = loadEntries(aospPath, partition, fileType)

                // Filter by search query
                val filteredOem = if (query.isNotBlank()) {
                    oemEntries.filter { it.pattern.contains(query, ignoreCase = true) || it.type.contains(query, ignoreCase = true) }
                } else oemEntries
                val filteredAosp = if (query.isNotBlank()) {
                    aospEntries.filter { it.pattern.contains(query, ignoreCase = true) || it.type.contains(query, ignoreCase = true) }
                } else aospEntries

                // Find common entries (pattern + type + fileType match)
                val common = filteredOem.filter { oem ->
                    filteredAosp.any { aosp ->
                        aosp.pattern == oem.pattern &&
                        aosp.type == oem.type &&
                        aosp.fileType == oem.fileType
                    }
                }

                // Remove common entries from individual lists
                val uniqueOem = filteredOem.filterNot { common.contains(it) }
                val uniqueAosp = filteredAosp.filterNot { common.contains(it) }

                _uiState.update {
                    it.copy(
                        oemEntries = uniqueOem,
                        aospEntries = uniqueAosp,
                        commonEntries = common,
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
                _events.emit(ContextsEvent.Error("Failed to load contexts: ${e.message}"))
            }
        }
    }

    private suspend fun loadEntries(basePath: String, partition: Partition, fileType: ContextFileType): List<ContextEntry> {
        val partitionPath = when (partition) {
            Partition.SYSTEM -> PolicyPartition.SYSTEM
            Partition.SYSTEM_EXT -> PolicyPartition.SYSTEM_EXT
            Partition.PRODUCT -> PolicyPartition.PRODUCT
            Partition.VENDOR -> PolicyPartition.VENDOR
            Partition.ODM -> PolicyPartition.ODM
        }

        val file = when (fileType) {
            ContextFileType.FILE -> pathResolver.fileContexts(basePath, partitionPath, ActiveMode.OFFLINE)
            ContextFileType.PROPERTY -> pathResolver.propertyContexts(basePath, partitionPath, ActiveMode.OFFLINE)
            ContextFileType.SERVICE -> pathResolver.serviceContexts(basePath, partitionPath, ActiveMode.OFFLINE)
            ContextFileType.HWSERVICE -> pathResolver.hwserviceContexts(basePath, partitionPath, ActiveMode.OFFLINE)
            ContextFileType.VNDSERVICE -> pathResolver.vndserviceContexts(basePath, ActiveMode.OFFLINE)
            ContextFileType.SEAPP -> pathResolver.seappContexts(basePath, partitionPath, ActiveMode.OFFLINE)
            ContextFileType.PROCESS -> return emptyList() // Not available offline
        }

        return if (file?.exists() == true) {
            contextParser.parse(
                content = file.readText(),
                sourceFile = file.absolutePath,
                fileType = fileType,
                partition = partition
            )
        } else {
            emptyList()
        }
    }

    fun selectPartition(partition: Partition) {
        _uiState.update { it.copy(selectedPartition = partition) }
        loadContexts()
    }

    fun selectFileType(fileType: ContextFileType) {
        _uiState.update { it.copy(selectedFileType = fileType) }
        loadContexts()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        // Reload with filter
        loadContexts()
    }
}

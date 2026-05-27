package com.selinuxtoolbox.feature.contexts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.model.ContextEntry
import com.selinuxtoolbox.core.model.ContextFileType
import com.selinuxtoolbox.core.model.Partition
import com.selinuxtoolbox.core.ui.components.SearchBar
import com.selinuxtoolbox.core.ui.theme.ReviewBlue
import com.selinuxtoolbox.core.ui.theme.EnforcingGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextsScreen(
    viewModel: ContextsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ContextsEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Context Viewer") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Filter controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Partition dropdown
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(0.4f)) {
                    OutlinedTextField(
                        value = uiState.selectedPartition.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Partition.values().forEach { partition ->
                            DropdownMenuItem(
                                text = { Text(partition.name) },
                                onClick = {
                                    viewModel.selectPartition(partition)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Context type dropdown
                var typeExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(0.6f)) {
                    OutlinedTextField(
                        value = uiState.selectedFileType.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth().clickable { typeExpanded = true }
                    )
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        ContextFileType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    viewModel.selectFileType(type)
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = { viewModel.loadContexts() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                placeholder = "Filter entries…"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Summary stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "OEM",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            uiState.oemEntries.size.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = ReviewBlue
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "AOSP",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            uiState.aospEntries.size.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = EnforcingGreen
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Common",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            uiState.commonEntries.size.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Entries list
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.oemEntries.isEmpty() && uiState.aospEntries.isEmpty() && uiState.commonEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "No context entries found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // OEM entries
                    items(uiState.oemEntries, key = { it.hashCode() }) { entry ->
                        ContextEntryRow(
                            entry = entry,
                            label = "OEM",
                            color = ReviewBlue
                        )
                    }
                    // AOSP entries
                    items(uiState.aospEntries, key = { it.hashCode() }) { entry ->
                        ContextEntryRow(
                            entry = entry,
                            label = "AOSP",
                            color = EnforcingGreen
                        )
                    }
                    // Common entries
                    items(uiState.commonEntries, key = { it.hashCode() }) { entry ->
                        ContextEntryRow(
                            entry = entry,
                            label = "BOTH",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextEntryRow(
    entry: ContextEntry,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.pattern,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = color.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.context,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${entry.fileType.name} · ${entry.sourceFile}:${entry.lineNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

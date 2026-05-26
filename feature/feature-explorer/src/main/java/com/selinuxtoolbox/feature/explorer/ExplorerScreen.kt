package com.selinuxtoolbox.feature.explorer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.domain.usecase.ManualSearchResult
import com.selinuxtoolbox.core.domain.usecase.TypeSearchResult
import com.selinuxtoolbox.core.ui.components.CodeBlock
import com.selinuxtoolbox.core.ui.theme.CriticalRed
import com.selinuxtoolbox.core.ui.theme.EnforcingGreen
import com.selinuxtoolbox.core.ui.theme.ReviewBlue

@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExplorerEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
                is ExplorerEvent.FixGenerated ->
                    snackbarHostState.showSnackbar("Fix generated: ${event.outputDir}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Type Explorer & Fix") }
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
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter type name (e.g. oplus_camera)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Error, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.search() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.searchQuery.isNotBlank() && !uiState.isSearching
            ) {
                if (uiState.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Search")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Results area
            when (val result = uiState.searchResult) {
                null -> {
                    // Nothing searched yet
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Enter a type name above to search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ManualSearchResult.NotFound -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, null, tint = CriticalRed)
                            Spacer(Modifier.height(8.dp))
                            Text("Type '${result.typeName}' not found in OEM or AOSP")
                        }
                    }
                }
                is ManualSearchResult.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(result.reason, color = CriticalRed)
                    }
                }
                is ManualSearchResult.Found -> {
                    SearchResultContent(
                        result = result.result,
                        onGenerate = { viewModel.generateFix() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultContent(
    result: TypeSearchResult,
    onGenerate: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            result.typeName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        SafetyBadge(result.safety)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        StatusIcon(result.inAosp, "In AOSP")
                        Spacer(modifier = Modifier.width(16.dp))
                        StatusIcon(result.inOem, "In OEM")
                    }
                }
            }
        }

        // AOSP sources
        if (result.aospSources.isNotEmpty()) {
            item {
                Text("Found in AOSP:", style = MaterialTheme.typography.titleSmall)
                result.aospSources.forEach { source ->
                    Text(
                        "  • $source",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // OEM declaration
        if (result.oemDeclaration != null) {
            item {
                Text("OEM declaration:", style = MaterialTheme.typography.titleSmall)
                CodeBlock(result.oemDeclaration)  // now safe because we checked != null
            }
        }

        // Context matches
        if (result.inContexts.isNotEmpty()) {
            item {
                Text("Context entries (${result.inContexts.size}):", style = MaterialTheme.typography.titleSmall)
                result.inContexts.forEach { ctx ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("${ctx.pattern} → ${ctx.context}", fontFamily = FontFamily.Monospace)
                            Text("${ctx.fileType} | ${ctx.sourceFile}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // RC matches
        if (result.inRcFiles.isNotEmpty()) {
            item {
                Text("RC seclabels (${result.inRcFiles.size}):", style = MaterialTheme.typography.titleSmall)
                result.inRcFiles.forEach { rc ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Service: ${rc.serviceName} → ${rc.execPath}", fontFamily = FontFamily.Monospace)
                            Text("${rc.rcFile}:${rc.lineNumber}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Suggested CIL
        item {
            Text("Suggested CIL:", style = MaterialTheme.typography.titleSmall)
            CodeBlock(result.suggestedCil)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onGenerate,
                enabled = result.canGenerate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (result.canGenerate) "Generate Fix" else "Cannot Generate")
            }
            if (!result.canGenerate) {
                Text(
                    if (result.inAosp) "Type already exists in AOSP — no fix needed"
                    else "Type is UNSAFE — manual review required",
                    style = MaterialTheme.typography.bodySmall,
                    color = CriticalRed
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(condition: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (condition) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (condition) EnforcingGreen else CriticalRed,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SafetyBadge(safety: com.selinuxtoolbox.core.domain.analyzer.TypeSafety) {
    val (label, color) = when (safety) {
        com.selinuxtoolbox.core.domain.analyzer.TypeSafety.SAFE -> "SAFE" to EnforcingGreen
        com.selinuxtoolbox.core.domain.analyzer.TypeSafety.REVIEW -> "REVIEW" to ReviewBlue
        com.selinuxtoolbox.core.domain.analyzer.TypeSafety.UNSAFE -> "UNSAFE" to CriticalRed
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

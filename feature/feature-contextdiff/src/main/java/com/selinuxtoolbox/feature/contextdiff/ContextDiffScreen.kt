package com.selinuxtoolbox.feature.contextdiff

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.model.ContextFileType
import com.selinuxtoolbox.core.model.Partition
import com.selinuxtoolbox.core.ui.components.CodeBlock
import com.selinuxtoolbox.core.ui.components.SearchBar
import com.selinuxtoolbox.core.ui.theme.EnforcingGreen
import com.selinuxtoolbox.core.ui.theme.ReviewBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextDiffScreen(
    viewModel: ContextDiffViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ContextDiffEvent.Error -> snackbarHostState.showSnackbar(event.message)
                is ContextDiffEvent.OutputReady -> snackbarHostState.showSnackbar("Output written to: ${event.outputDir}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Context Diff & Generator") }
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
            when (val step = uiState.step) {
                is ContextDiffStep.Idle -> IdleContent(
                    onRunComparison = viewModel::runComparison,
                    projectName = uiState.projectName
                )
                is ContextDiffStep.Loading -> LoadingContent()
                is ContextDiffStep.Results -> ResultsContent(
                    missingEntries = step.missingEntries,
                    acceptedIds = uiState.acceptedIds,
                    onToggle = viewModel::toggleAccept,
                    onGenerate = viewModel::generateOutput
                )
                is ContextDiffStep.Generating -> GeneratingContent()
                is ContextDiffStep.Done -> DoneContent(
                    outputDir = step.outputDir,
                    onReset = viewModel::reset
                )
                is ContextDiffStep.Error -> ErrorContent(
                    message = step.message,
                    onRetry = viewModel::reset
                )
                is ContextDiffStep.NoProject -> NoProjectMessage()
            }
        }
    }
}

@Composable
private fun IdleContent(
    projectName: String,
    onRunComparison: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.AutoMirrored.Filled.CompareArrows, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Context File Diff & Generator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Project: $projectName", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scans OEM context files (file, property, service, etc.) and finds entries\n" +
            "that are missing from AOSP. Generates type declarations + context entries.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRunComparison, modifier = Modifier.fillMaxWidth(0.6f)) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Run Comparison")
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Scanning context files…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultsContent(
    missingEntries: List<ContextDiffEntry>,
    acceptedIds: Set<String>,
    onToggle: (String) -> Unit,
    onGenerate: () -> Unit
) {
    val acceptedCount = acceptedIds.size

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary strip
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryChip("${missingEntries.size} missing", MaterialTheme.colorScheme.error)
                SummaryChip("$acceptedCount accepted", EnforcingGreen)
            }
        }

        // Search
        var query by remember { mutableStateOf("") }
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Filter by type or pattern…"
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filtered = missingEntries.filter {
                query.isBlank() ||
                it.type.contains(query, ignoreCase = true) ||
                it.pattern.contains(query, ignoreCase = true)
            }
            items(filtered, key = { it.id }) { entry ->
                ContextEntryCard(
                    entry = entry,
                    accepted = entry.id in acceptedIds,
                    onToggle = { onToggle(entry.id) }
                )
            }
        }

        // Generate button
        Surface(shadowElevation = 8.dp) {
            Button(
                onClick = onGenerate,
                enabled = acceptedCount > 0,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Text("Generate CIL for $acceptedCount entry(s)")
            }
        }
    }
}

@Composable
private fun ContextEntryCard(
    entry: ContextDiffEntry,
    accepted: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (accepted) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = accepted,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.pattern,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    entry.context,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        entry.fileType.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = ReviewBlue
                    )
                    Text(
                        entry.partition,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Type: ${entry.type}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun GeneratingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Generating CIL and context files…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DoneContent(outputDir: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = EnforcingGreen, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Output generated", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = EnforcingGreen)
        Spacer(Modifier.height(8.dp))
        Text("Files written to:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedCard {
            Text(
                outputDir,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Run Another Diff")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Error, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) { Text("Go Back") }
    }
}

@Composable
private fun NoProjectMessage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("No active project", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Create or open a project first.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

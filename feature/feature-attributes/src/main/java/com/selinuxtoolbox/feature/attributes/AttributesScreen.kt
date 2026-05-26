package com.selinuxtoolbox.feature.attributes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import com.selinuxtoolbox.core.ui.theme.EnforcingGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributesScreen(
    viewModel: AttributesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AttributesEvent.Error -> snackbarHostState.showSnackbar(event.message)
                is AttributesEvent.OutputReady -> snackbarHostState.showSnackbar("Output written to: ${event.outputDir}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Missing Typeattributes") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val step = uiState.step) {
                is AttributesStep.NoProject -> NoProjectMessage()
                is AttributesStep.Idle -> IdleContent(
                    projectName = uiState.projectName,
                    onScan = viewModel::scanAndFix
                )
                is AttributesStep.Scanning -> LoadingContent()
                is AttributesStep.Results -> ResultsContent(
                    step = step,
                    onBack = viewModel::reset
                )
                is AttributesStep.Error -> ErrorContent(
                    message = step.message,
                    onRetry = viewModel::reset
                )
                is AttributesStep.Done -> DoneContent(
                    outputDir = step.outputDir,
                    onBack = viewModel::reset
                )
            }
        }
    }
}

@Composable
private fun NoProjectMessage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No active project. Create or open a project first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdleContent(
    projectName: String,
    onScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Attribute,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Missing Typeattribute Fixer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Project: $projectName",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Scans generated CIL files for (typeattributeset ...) and adds missing (typeattribute) declarations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan & Generate Missing Attributes")
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Scanning generated CIL files for missing attributes…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultsContent(
    step: AttributesStep.Results,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Analysis Complete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Found ${step.missingCount} missing attribute(s)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        step.allCilFile?.let { allCil ->
            item {
                OutputFileCard(
                    title = "All Missing Attributes (combined)",
                    fileName = allCil.substringAfterLast("/"),
                    destination = "AOSP/vendor/selinux/",
                    action = "NEW FILE — load before vendor_sepolicy.cil"
                )
            }
        }

        step.partitionFiles.forEach { (partition, path) ->
            item {
                OutputFileCard(
                    title = "${partition.replaceFirstChar { it.uppercase() }} Attributes",
                    fileName = path.substringAfterLast("/"),
                    destination = "AOSP/${partition}/selinux/",
                    action = "NEW FILE (optional per-partition file)"
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun OutputFileCard(
    title: String,
    fileName: String,
    destination: String,
    action: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                fileName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "→ $destination",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                action,
                style = MaterialTheme.typography.labelSmall,
                color = EnforcingGreen
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Error, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) { Text("Try Again") }
    }
}

@Composable
private fun DoneContent(
    outputDir: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = EnforcingGreen, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Output Generated", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = EnforcingGreen)
        Spacer(Modifier.height(8.dp))
        Text("Output written to:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedCard {
            Text(
                outputDir,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Open INSTRUCTIONS.txt in the output folder for load order details.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

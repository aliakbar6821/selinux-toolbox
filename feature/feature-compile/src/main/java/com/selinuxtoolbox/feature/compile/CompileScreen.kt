package com.selinuxtoolbox.feature.compile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.selinuxtoolbox.core.domain.usecase.PreCheckResult
import com.selinuxtoolbox.core.domain.usecase.SecilcError

@Composable
fun CompileScreen(
    viewModel: CompileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compile Policy") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val step = uiState.step) {
                is CompileStep.NoProject ->
                    CenteredMessage("No active project. Create or open a project first.")

                is CompileStep.Initializing ->
                    CenteredLoading("Initializing secilc binary…")

                is CompileStep.Idle ->
                    IdleContent(
                        projectName    = uiState.projectName,
                        outputDir      = uiState.outputDir,
                        mappingVersion = uiState.mappingVersion,
                        policyVersion  = uiState.policyVersion,
                        onVersionChange = viewModel::setPolicyVersion,
                        onCompile      = viewModel::compile,
                        onRetryBinary  = viewModel::retryBinaryInit
                    )

                is CompileStep.DryRunning ->
                    CenteredLoading("Running dry-run (secilc -o /dev/null)…")

                is CompileStep.Compiling ->
                    CenteredLoading("Compiling precompiled_sepolicy…")

                is CompileStep.Success ->
                    SuccessContent(step, onBack = viewModel::reset)

                is CompileStep.DryRunFailed ->
                    DryRunFailedContent(step, onBack = viewModel::reset)

                is CompileStep.CompileFailed ->
                    CompileFailedContent(step, onBack = viewModel::reset)

                is CompileStep.SetupError ->
                    CenteredMessage(step.reason, isError = true, onBack = viewModel::reset)
            }
        }
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(
    projectName: String,
    outputDir: String,
    mappingVersion: String,
    policyVersion: Int,
    onVersionChange: (Int) -> Unit,
    onCompile: () -> Unit,
    onRetryBinary: () -> Unit
) {
    var showVersionPicker by remember { mutableStateOf(false) }

    // Generate versions dynamically based on mapping version
    val baseVersion = runCatching { mappingVersion.substringBefore('.').toInt() }.getOrElse { 35 }
    val versions = (baseVersion - 5..baseVersion + 2).filter { it >= 30 }.sorted()

    LazyColumn(
        modifier       = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Compile Policy",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Project: $projectName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Output",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$outputDir/precompiled_sepolicy",
                        style      = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Policy Version (-c)",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        versions.forEach { v ->
                            FilterChip(
                                selected = policyVersion == v,
                                onClick  = { onVersionChange(v) },
                                label    = { Text("$v") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "How it works",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Dry-run: secilc -o /dev/null — catches errors without writing.\n" +
                        "2. If clean, real compile: writes precompiled_sepolicy to your output folder.\n" +
                        "3. Flash with: adb push precompiled_sepolicy /vendor/etc/selinux/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRetryBinary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Retry Binary Initialization")
                    }
                }
            }
        }

        item {
            Button(
                onClick  = onCompile,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Build, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Dry-run then Compile", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ── Success ───────────────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(step: CompileStep.Success, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color    = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier          = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint     = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Compiled successfully",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "${step.filesCompiled} CIL files · ${step.sizeKb} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Output file",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            step.outputPath,
                            style      = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Next steps",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "adb push ${step.outputPath} /vendor/etc/selinux/precompiled_sepolicy\n" +
                            "adb reboot",
                            style      = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Surface(shadowElevation = 8.dp) {
            OutlinedButton(
                onClick  = onBack,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) { Text("Back") }
        }
    }
}

// ── Dry-run failed ────────────────────────────────────────────────────────────

@Composable
private fun DryRunFailedContent(step: CompileStep.DryRunFailed, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color    = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier          = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Error,
                    null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Dry-run failed",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Fix errors in the Validator before compiling",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (step.preChecks.any { !it.passed }) {
                item {
                    Text(
                        "Pre-check failures",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(step.preChecks.filter { !it.passed }) { check ->
                    PreCheckCard(check)
                }
            }

            if (step.errors.isNotEmpty()) {
                item {
                    Text(
                        "secilc errors (${step.errors.size})",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.error,
                        modifier   = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(step.errors) { error -> ErrorCard(error) }
            }
        }

        Surface(shadowElevation = 8.dp) {
            OutlinedButton(
                onClick  = onBack,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) { Text("Back") }
        }
    }
}

// ── Compile failed ────────────────────────────────────────────────────────────

@Composable
private fun CompileFailedContent(step: CompileStep.CompileFailed, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color    = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Compilation failed",
                modifier   = Modifier.padding(16.dp),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(step.stderr) { line ->
                Text(
                    line,
                    style      = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color      = MaterialTheme.colorScheme.error
                )
            }
        }

        Surface(shadowElevation = 8.dp) {
            OutlinedButton(
                onClick  = onBack,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) { Text("Back") }
        }
    }
}

// ── Shared cards ──────────────────────────────────────────────────────────────

@Composable
private fun PreCheckCard(check: PreCheckResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                check.fileName,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onErrorContainer
            )
            check.issues.forEach { issue ->
                Text(
                    issue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(error: SecilcError) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    error.file,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onErrorContainer
                )
                if (error.line != null) Text(
                    ":${error.line}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                error.message,
                style      = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color      = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun CenteredLoading(message: String) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    isError: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
        )
        if (onBack != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

package com.selinuxtoolbox.feature.cleanup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.domain.analyzer.CleanupResult
import com.selinuxtoolbox.core.model.RuleClassification
import com.selinuxtoolbox.core.ui.components.ClassificationChip
import com.selinuxtoolbox.core.ui.components.FullScreenError
import com.selinuxtoolbox.core.ui.components.OperationProgressBar
import com.selinuxtoolbox.core.ui.theme.CriticalRed
import com.selinuxtoolbox.core.ui.theme.EnforcingGreen
import com.selinuxtoolbox.core.ui.theme.OrphanedGrey
import com.selinuxtoolbox.core.ui.theme.PermissiveAmber
import com.selinuxtoolbox.core.ui.theme.ReviewBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    viewModel: CleanupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CleanupEvent.Error ->
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                is CleanupEvent.SavedSuccess ->
                    snackbarHostState.showSnackbar(
                        "Saved ${event.filesModified} file(s) to ${event.outputDir}"
                    )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Policy Cleanup",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val phase = uiState.phase) {
                is CleanupUiPhase.Idle -> {
                    CleanupIdleContent(
                        onStartAnalysis = {
                            // In a real flow the policy is passed from the active project.
                            // For now the button is wired but shows an informational message
                            // until the policy loading use case is connected in a later step.
                            snackbarHostState.let { host ->
                                // Non-composable context — handled via event in future step
                            }
                        }
                    )
                }

                is CleanupUiPhase.Analyzing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        uiState.progress?.let { progress ->
                            OperationProgressBar(progress = progress)
                        }
                    }
                }

                is CleanupUiPhase.ReviewingResults -> {
                    uiState.result?.let { result ->
                        CleanupResultContent(
                            result = result,
                            onApply = {
                                // projectId and policy wired in future step
                                // when active project integration is added
                            },
                            onReset = viewModel::reset
                        )
                    }
                }

                is CleanupUiPhase.Applying -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Applying cleanup…",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                is CleanupUiPhase.Done -> {
                    CleanupDoneContent(
                        outputDir = uiState.outputDir ?: "",
                        filesModified = uiState.filesModified,
                        onReset = viewModel::reset
                    )
                }

                is CleanupUiPhase.Failed -> {
                    FullScreenError(
                        message = phase.reason,
                        onRetry = viewModel::reset
                    )
                }
            }
        }
    }
}

// ─── Idle ────────────────────────────────────────────────────────────────────

@Composable
private fun CleanupIdleContent(onStartAnalysis: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CleaningServices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Policy Cleanup",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Analyzes all CIL files in the active project and identifies:\n\n" +
                   "• Orphaned types — declared but never used\n" +
                   "• Wrong SoC types — e.g. qcom_ types on an MTK device\n" +
                   "• Wrong ROM types — donor ROM types that don't belong\n" +
                   "• Protected types — referenced by mapping files (never touched)\n\n" +
                   "Select an active project first, then run analysis.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStartAnalysis,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.CleaningServices,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Start Analysis")
        }
    }
}

// ─── Results ─────────────────────────────────────────────────────────────────

@Composable
private fun CleanupResultContent(
    result: CleanupResult,
    onApply: () -> Unit,
    onReset: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary card
        item {
            CleanupSummaryCard(result = result)
        }

        // Orphaned types
        if (result.orphanedTypes.isNotEmpty()) {
            item {
                TypeGroupCard(
                    title = "Orphaned Types",
                    subtitle = "Declared but never used anywhere",
                    types = result.orphanedTypes.toList(),
                    classification = RuleClassification.ORPHANED
                )
            }
        }

        // Wrong SoC types
        if (result.wrongSocTypes.isNotEmpty()) {
            item {
                TypeGroupCard(
                    title = "Wrong SoC Types",
                    subtitle = "SoC prefix does not match this device",
                    types = result.wrongSocTypes.toList(),
                    classification = RuleClassification.WRONG_SOC
                )
            }
        }

        // Wrong ROM types
        if (result.wrongRomTypes.isNotEmpty()) {
            item {
                TypeGroupCard(
                    title = "Wrong ROM Types",
                    subtitle = "ROM prefix belongs to donor ROM, not target",
                    types = result.wrongRomTypes.toList(),
                    classification = RuleClassification.WRONG_ROM
                )
            }
        }

        // Protected types info
        if (result.protectedTypes.isNotEmpty()) {
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = ReviewBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                "${result.protectedTypes.size} Protected Types",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ReviewBlue
                            )
                            Text(
                                "Referenced by mapping/ files — will never be removed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Action buttons
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply Cleanup")
                }
            }
        }
    }
}

@Composable
private fun CleanupSummaryCard(result: CleanupResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Analysis Complete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryCount(
                    label = "Total Rules",
                    count = result.totalRules,
                    color = MaterialTheme.colorScheme.onSurface
                )
                SummaryCount(
                    label = "Orphaned",
                    count = result.orphanedTypes.size,
                    color = OrphanedGrey
                )
                SummaryCount(
                    label = "Wrong SoC",
                    count = result.wrongSocTypes.size,
                    color = CriticalRed
                )
                SummaryCount(
                    label = "Wrong ROM",
                    count = result.wrongRomTypes.size,
                    color = PermissiveAmber
                )
            }
        }
    }
}

@Composable
private fun SummaryCount(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TypeGroupCard(
    title: String,
    subtitle: String,
    types: List<String>,
    classification: RuleClassification
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ClassificationChip(classification = classification)
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            // Show up to 20 types — full list in a future detail screen
            types.take(20).forEach { typeName ->
                Text(
                    text = typeName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (types.size > 20) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "… and ${types.size - 20} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Done ────────────────────────────────────────────────────────────────────

@Composable
private fun CleanupDoneContent(
    outputDir: String,
    filesModified: Int,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = EnforcingGreen,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Cleanup Applied",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = EnforcingGreen
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "$filesModified file(s) written to output directory",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            outputDir,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run Another Analysis")
        }
    }
}

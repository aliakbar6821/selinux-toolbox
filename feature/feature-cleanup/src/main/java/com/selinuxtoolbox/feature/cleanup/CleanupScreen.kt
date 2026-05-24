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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.model.RuleAnalysisResult
import com.selinuxtoolbox.core.model.RuleClassification
import com.selinuxtoolbox.core.model.SuggestedAction
import com.selinuxtoolbox.core.ui.components.ClassificationChip
import com.selinuxtoolbox.core.ui.components.CodeBlock
import com.selinuxtoolbox.core.ui.components.ConfirmationDialog
import com.selinuxtoolbox.core.ui.components.EmptyState
import com.selinuxtoolbox.core.ui.components.FullScreenError
import com.selinuxtoolbox.core.ui.components.OperationProgressBar
import com.selinuxtoolbox.core.ui.components.SectionHeader
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
    var showApplyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CleanupEvent.Error ->
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                is CleanupEvent.PolicyLoaded ->
                    snackbarHostState.showSnackbar("Policy loaded — ready to analyze")
                is CleanupEvent.AnalysisComplete ->
                    snackbarHostState.showSnackbar(
                        "Analysis complete: ${event.totalRules} rules, " +
                        "${event.toRemove} flagged for removal"
                    )
                is CleanupEvent.SaveComplete ->
                    snackbarHostState.showSnackbar(
                        "Saved ${event.count} file(s) to ${event.outputDir}"
                    )
            }
        }
    }

    if (showApplyDialog) {
        ConfirmationDialog(
            title = "Apply Cleanup",
            message = "Write cleaned CIL files to your project output folder on SD card?\n\n" +
                      "A full backup will be created before any changes are made. " +
                      "This does NOT modify live partitions.",
            confirmLabel = "Apply",
            destructive = false,
            onConfirm = {
                showApplyDialog = false
                viewModel.applyCleanup()
            },
            onDismiss = { showApplyDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate Clean SELinux") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.error != null && uiState.cleanupResult == null -> {
                FullScreenError(
                    message = uiState.error!!,
                    onRetry = { viewModel.clearError(); viewModel.loadPolicy() }
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Step controls
                    item {
                        CleanupControlCard(
                            hasPolicyLoaded = uiState.policy != null,
                            hasResult = uiState.cleanupResult != null,
                            isLoadingPolicy = uiState.isLoadingPolicy,
                            isAnalyzing = uiState.isAnalyzing,
                            isApplying = uiState.isApplying,
                            activeProjectName = uiState.activeProject?.name,
                            onLoadPolicy = { viewModel.loadPolicy() },
                            onRunAnalysis = { viewModel.runAnalysis() },
                            onApply = { showApplyDialog = true }
                        )
                    }

                    // Progress bar
                    uiState.analysisProgress?.let { progress ->
                        item {
                            OperationProgressBar(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Summary cards
                    uiState.cleanupResult?.let { result ->
                        item { CleanupSummaryRow(result) }

                        // Filter chips
                        item {
                            SectionHeader(
                                title = "Results (${uiState.filteredResults.size})",
                                trailingContent = {
                                    Text(
                                        text = if (uiState.selectedFilter == null) "All"
                                               else uiState.selectedFilter!!.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }

                        item {
                            FilterChipsRow(
                                result = result,
                                selectedFilter = uiState.selectedFilter,
                                onFilterSelected = { viewModel.setFilter(it) }
                            )
                        }

                        // Results list
                        if (uiState.filteredResults.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Default.CleaningServices,
                                    title = "No rules match this filter"
                                )
                            }
                        } else {
                            items(
                                items = uiState.filteredResults.take(500), // cap for performance
                                key = {
                                    "${it.statement.sourceFile}:${it.statement.lineNumber}"
                                }
                            ) { ruleResult ->
                                RuleResultCard(result = ruleResult)
                            }
                            if (uiState.filteredResults.size > 500) {
                                item {
                                    Text(
                                        text = "… and ${uiState.filteredResults.size - 500} more",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Control card — steps 1, 2, 3
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CleanupControlCard(
    hasPolicyLoaded: Boolean,
    hasResult: Boolean,
    isLoadingPolicy: Boolean,
    isAnalyzing: Boolean,
    isApplying: Boolean,
    activeProjectName: String?,
    onLoadPolicy: () -> Unit,
    onRunAnalysis: () -> Unit,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Workflow",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            activeProjectName?.let { name ->
                Text(
                    "Project: $name",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: Text(
                "⚠ No active project — create one in Projects first",
                style = MaterialTheme.typography.bodySmall,
                color = PermissiveAmber
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Step 1: Load Policy
                OutlinedButton(
                    onClick = onLoadPolicy,
                    enabled = !isLoadingPolicy && !isAnalyzing && !isApplying,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoadingPolicy) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp))
                    } else {
                        Text(if (hasPolicyLoaded) "Reload Policy" else "Load Policy")
                    }
                }

                // Step 2: Analyze
                Button(
                    onClick = onRunAnalysis,
                    enabled = hasPolicyLoaded && !isAnalyzing && !isApplying,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp))
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.height(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Analyze")
                    }
                }

                // Step 3: Apply
                Button(
                    onClick = onApply,
                    enabled = hasResult && !isAnalyzing && !isApplying &&
                              activeProjectName != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EnforcingGreen
                    )
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp))
                    } else {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.height(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }

            Text(
                "Output is written to SD card project folder — live partitions are never touched",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary row — count cards per classification
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CleanupSummaryRow(result: com.selinuxtoolbox.core.domain.analyzer.CleanupResult) {
    val counts = mapOf(
        RuleClassification.ACTIVE    to result.results.count { it.classification == RuleClassification.ACTIVE },
        RuleClassification.ORPHANED  to result.results.count { it.classification == RuleClassification.ORPHANED },
        RuleClassification.WRONG_SOC to result.results.count { it.classification == RuleClassification.WRONG_SOC },
        RuleClassification.WRONG_ROM to result.results.count { it.classification == RuleClassification.WRONG_ROM },
        RuleClassification.PROTECTED to result.results.count { it.classification == RuleClassification.PROTECTED },
        RuleClassification.REVIEW    to result.results.count { it.classification == RuleClassification.REVIEW }
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(counts.entries.toList()) { (classification, count) ->
            if (count > 0) {
                SummaryCountCard(classification = classification, count = count)
            }
        }
    }
}

@Composable
private fun SummaryCountCard(classification: RuleClassification, count: Int) {
    val color = when (classification) {
        RuleClassification.ACTIVE    -> EnforcingGreen
        RuleClassification.ORPHANED  -> OrphanedGrey
        RuleClassification.WRONG_SOC -> CriticalRed
        RuleClassification.WRONG_ROM -> PermissiveAmber
        RuleClassification.PROTECTED -> ReviewBlue
        RuleClassification.REVIEW    -> ReviewBlue
        RuleClassification.UNKNOWN   -> OrphanedGrey
    }
    OutlinedCard {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = classification.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter chips
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    result: com.selinuxtoolbox.core.domain.analyzer.CleanupResult,
    selectedFilter: RuleClassification?,
    onFilterSelected: (RuleClassification?) -> Unit
) {
    val classifications = listOf(null) +
        RuleClassification.entries.filter { cls ->
            result.results.any { it.classification == cls }
        }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(classifications) { cls ->
            FilterChip(
                selected = selectedFilter == cls,
                onClick = { onFilterSelected(cls) },
                label = {
                    Text(
                        cls?.name?.replace("_", " ") ?: "All",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual rule result card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RuleResultCard(result: RuleAnalysisResult) {
    val borderColor = when (result.classification) {
        RuleClassification.ACTIVE    -> EnforcingGreen.copy(alpha = 0.3f)
        RuleClassification.ORPHANED  -> OrphanedGrey.copy(alpha = 0.3f)
        RuleClassification.WRONG_SOC -> CriticalRed.copy(alpha = 0.3f)
        RuleClassification.WRONG_ROM -> PermissiveAmber.copy(alpha = 0.3f)
        RuleClassification.PROTECTED -> ReviewBlue.copy(alpha = 0.3f)
        RuleClassification.REVIEW    -> ReviewBlue.copy(alpha = 0.3f)
        RuleClassification.UNKNOWN   -> OrphanedGrey.copy(alpha = 0.3f)
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClassificationChip(
                    classification = result.classification
                )
                Text(
                    text = actionLabel(result.suggestedAction),
                    style = MaterialTheme.typography.labelSmall,
                    color = actionColor(result.suggestedAction),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // File + line
            Text(
                text = "${result.statement.sourceFile.substringAfterLast('/')}:" +
                       "${result.statement.lineNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Raw CIL statement representation
            val rawStatement = describeStatement(result.statement)
            if (rawStatement.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                CodeBlock(code = rawStatement, maxLines = 3)
            }

            // Reason
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun actionLabel(action: SuggestedAction): String = when (action) {
    SuggestedAction.KEEP          -> "KEEP"
    SuggestedAction.REMOVE        -> "REMOVE"
    SuggestedAction.COMMENT_OUT   -> "COMMENT OUT"
    SuggestedAction.MANUAL_REVIEW -> "MANUAL REVIEW"
}

@Composable
private fun actionColor(action: SuggestedAction) = when (action) {
    SuggestedAction.KEEP          -> EnforcingGreen
    SuggestedAction.REMOVE        -> CriticalRed
    SuggestedAction.COMMENT_OUT   -> PermissiveAmber
    SuggestedAction.MANUAL_REVIEW -> ReviewBlue
}

private fun describeStatement(stmt: com.selinuxtoolbox.core.model.CilStatement): String {
    return when (stmt) {
        is com.selinuxtoolbox.core.model.CilStatement.TypeDeclaration ->
            "(type ${stmt.name})"
        is com.selinuxtoolbox.core.model.CilStatement.AllowRule ->
            "(allow ${stmt.source} ${stmt.target} (${stmt.objectClass} " +
            "(${stmt.permissions.joinToString(" ")})))"
        is com.selinuxtoolbox.core.model.CilStatement.TypeAttributeSet ->
            "(typeattributeset ${stmt.attribute} (${stmt.members.take(5).joinToString(" ")}…))"
        is com.selinuxtoolbox.core.model.CilStatement.GenericStatement ->
            "(${stmt.keyword} ${stmt.rawContent.take(60)})"
        else -> ""
    }
}

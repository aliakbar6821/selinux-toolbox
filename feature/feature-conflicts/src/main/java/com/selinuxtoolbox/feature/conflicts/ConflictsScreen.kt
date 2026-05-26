package com.selinuxtoolbox.feature.conflicts

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
import com.selinuxtoolbox.core.domain.usecase.Conflict
import com.selinuxtoolbox.core.model.ConflictSeverity
import com.selinuxtoolbox.core.ui.components.SeverityChip
import com.selinuxtoolbox.core.ui.theme.CriticalRed

@Composable
fun ConflictsScreen(
    viewModel: ConflictsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConflictsEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rule Conflicts") },
                actions = {
                    IconButton(onClick = { viewModel.detectConflicts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
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
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            if (uiState.report == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap refresh to scan for conflicts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Scaffold
            }

            val report = uiState.report

            // Summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SummaryCard("Total", report.totalConflicts, MaterialTheme.colorScheme.primary)
                SummaryCard("Critical", report.criticalCount, CriticalRed)
                SummaryCard("High", report.highCount, MaterialTheme.colorScheme.error)
                SummaryCard("Medium", report.mediumCount, MaterialTheme.colorScheme.secondary)
                SummaryCard("Low", report.lowCount, MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Conflicts list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (report.duplicateTypes.isNotEmpty()) {
                    item {
                        SectionHeader("Duplicate Types (${report.duplicateTypes.size})")
                    }
                    items(report.duplicateTypes, key = { it.hashCode() }) { conflict ->
                        ConflictCard(conflict)
                    }
                }

                if (report.duplicateRules.isNotEmpty()) {
                    item {
                        SectionHeader("Duplicate Rules (${report.duplicateRules.size})")
                    }
                    items(report.duplicateRules, key = { it.hashCode() }) { conflict ->
                        ConflictCard(conflict)
                    }
                }

                if (report.contradictingRules.isNotEmpty()) {
                    item {
                        SectionHeader("Contradicting Rules (${report.contradictingRules.size})")
                    }
                    items(report.contradictingRules, key = { it.hashCode() }) { conflict ->
                        ConflictCard(conflict)
                    }
                }

                if (report.mappingMismatches.isNotEmpty()) {
                    item {
                        SectionHeader("Mapping Mismatches (${report.mappingMismatches.size})")
                    }
                    items(report.mappingMismatches, key = { it.hashCode() }) { conflict ->
                        ConflictCard(conflict)
                    }
                }

                if (report.missingPlatformTypes.isNotEmpty()) {
                    item {
                        SectionHeader("Missing Platform Types (${report.missingPlatformTypes.size})")
                    }
                    items(report.missingPlatformTypes, key = { it.hashCode() }) { conflict ->
                        ConflictCard(conflict)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ConflictCard(conflict: Conflict) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (conflict.severity) {
                ConflictSeverity.CRITICAL -> CriticalRed.copy(alpha = 0.1f)
                ConflictSeverity.HIGH -> MaterialTheme.colorScheme.errorContainer
                ConflictSeverity.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
                ConflictSeverity.LOW -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    conflict.description,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                SeverityChip(severity = conflict.severity)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${conflict.file1}${conflict.file2?.let { " vs $it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

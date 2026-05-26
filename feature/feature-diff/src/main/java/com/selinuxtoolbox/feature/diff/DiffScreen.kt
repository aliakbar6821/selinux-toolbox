package com.selinuxtoolbox.feature.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.domain.usecase.DiffLine
import com.selinuxtoolbox.core.domain.usecase.DiffReport

@Composable
fun DiffScreen(
    viewModel: DiffViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Policy Diff") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val step = uiState.step) {
                is DiffStep.NoProject ->
                    CenteredMessage("No active project. Create or open a project first.")

                is DiffStep.Loading ->
                    CenteredLoading("Scanning generated CIL files…")

                is DiffStep.NoOutputs ->
                    CenteredMessage(
                        "No generated CIL files found in:\n${step.workPath}/outputs/\n\n" +
                        "Run the diff or cleanup step first to generate additions."
                    )

                is DiffStep.Error ->
                    CenteredMessage(step.reason, isError = true)

                is DiffStep.Ready ->
                    ReadyContent(
                        report       = step.report,
                        projectName  = uiState.projectName,
                        activeFilter = uiState.activeFilter,
                        visibleLines = uiState.visibleLines,
                        onFilter     = viewModel::setFilter
                    )
            }
        }
    }
}

// ── Ready content ─────────────────────────────────────────────────────────────

@Composable
private fun ReadyContent(
    report: DiffReport,
    projectName: String,
    activeFilter: DiffFilter,
    visibleLines: List<DiffLine>,
    onFilter: (DiffFilter) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // Summary header
        Surface(
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Project: $projectName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SummaryChip("${report.addedTypes.size} types",      MaterialTheme.colorScheme.tertiary) }
                    item { SummaryChip("${report.addedAttributes.size + report.addedAttributeSets.size} attrs", MaterialTheme.colorScheme.secondary) }
                    item { SummaryChip("${report.addedRules.size} rules",       MaterialTheme.colorScheme.primary) }
                    item { SummaryChip("${report.addedContextEntries.size} ctx", MaterialTheme.colorScheme.onSurfaceVariant) }
                    item {
                        Text(
                            "· ${report.generatedFileCount} files scanned",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }

        // Filter chips
        LazyRow(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DiffFilter.values()) { filter ->
                val count = when (filter) {
                    DiffFilter.ALL       -> report.totalAdditions
                    DiffFilter.TYPES     -> report.addedTypes.size
                    DiffFilter.ATTRIBUTES-> report.addedAttributes.size + report.addedAttributeSets.size
                    DiffFilter.RULES     -> report.addedRules.size
                    DiffFilter.CONTEXTS  -> report.addedContextEntries.size
                }
                FilterChip(
                    selected = activeFilter == filter,
                    onClick  = { onFilter(filter) },
                    label    = { Text("${filter.label} ($count)") }
                )
            }
        }

        if (report.isEmpty) {
            CenteredMessage("No additions found in generated CIL files.")
            return@Column
        }

        // Lines list
        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Group by source file
            val grouped = visibleLines.groupBy { it.sourceFile }
            grouped.forEach { (fileName, lines) ->
                item(key = "header_$fileName") {
                    Text(
                        fileName,
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        modifier   = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(lines, key = { i, l -> "${fileName}_${l.lineNumber}_$i" }) { _, line ->
                    DiffLineCard(line)
                }
            }
        }
    }
}

// ── Diff line card ────────────────────────────────────────────────────────────

@Composable
private fun DiffLineCard(line: DiffLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Line number badge
        Text(
            "${line.lineNumber}",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
            fontSize = 10.sp
        )
        // CIL content
        Text(
            line.content,
            style      = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize   = 11.sp,
            color      = MaterialTheme.colorScheme.onSurface,
            modifier   = Modifier.weight(1f)
        )
    }
}

// ── Summary chip ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = color
        )
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
    isError: Boolean = false
) {
    Box(
        modifier         = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

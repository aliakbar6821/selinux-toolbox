package com.selinuxtoolbox.feature.offline

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.domain.analyzer.RcScanResult
import com.selinuxtoolbox.core.domain.analyzer.SeclabelEntry
import com.selinuxtoolbox.core.domain.analyzer.TypeSafety
import com.selinuxtoolbox.core.ui.components.EmptyState
import com.selinuxtoolbox.core.ui.components.FullScreenError
import com.selinuxtoolbox.core.ui.components.SearchBar
import com.selinuxtoolbox.core.ui.theme.CriticalRed
import com.selinuxtoolbox.core.ui.theme.EnforcingGreen
import com.selinuxtoolbox.core.ui.theme.OrphanedGrey
import com.selinuxtoolbox.core.ui.theme.PermissiveAmber
import com.selinuxtoolbox.core.ui.theme.ReviewBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RcScanScreen(
    viewModel: RcScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RcScanEvent.OutputReady ->
                    snackbarHostState.showSnackbar("Output written to: ${event.outputDir}")
                is RcScanEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "RC Seclabel Scanner",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (uiState.phase is RcScanPhase.Results ||
                        uiState.phase is RcScanPhase.Done ||
                        uiState.phase is RcScanPhase.Error) {
                        IconButton(onClick = viewModel::reset) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val phase = uiState.phase) {
                is RcScanPhase.Idle -> IdleContent(
                    project = uiState.activeProject,
                    onStartScan = viewModel::startScan
                )
                is RcScanPhase.Scanning -> ScanningContent()
                is RcScanPhase.Results -> ResultsContent(
                    result           = phase.result,
                    confirmedDomains = uiState.confirmedDomains,
                    filterText       = uiState.filterText,
                    onFilterChange   = viewModel::onFilterChange,
                    onToggle         = viewModel::toggleConfirm,
                    onConfirmAll     = { viewModel.confirmAll(it) },
                    onConfirmNone    = { viewModel.confirmNone(it) },
                    onGenerate       = viewModel::generateOutput
                )
                is RcScanPhase.Generating -> GeneratingContent()
                is RcScanPhase.Done -> DoneContent(
                    outputDir = phase.outputDir,
                    onReset   = viewModel::reset
                )
                is RcScanPhase.Error -> FullScreenError(
                    message = phase.message,
                    onRetry = viewModel::reset
                )
            }
        }
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(
    project: com.selinuxtoolbox.core.model.Project?,
    onStartScan: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "RC Seclabel Scanner",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Scans OEM init RC files for seclabel declarations.\n" +
                   "Finds domains missing from the AOSP (Moto) policy.\n" +
                   "Generates CIL domain + exec + transition blocks\n" +
                   "and file_contexts entries to add to the port.\n\n" +
                   "Source A from the project brief.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (project != null) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Active project:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(project.name, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("OEM:  ${project.oemPath.ifBlank { "(not set)" }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("AOSP: ${project.aospPath.ifBlank { "(not set)" }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Scan")
            }
        } else {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No active project. Go to Projects and set one active.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Scanning ──────────────────────────────────────────────────────────────────

@Composable
private fun ScanningContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Text("Scanning OEM RC files…", style = MaterialTheme.typography.titleMedium)
            Text(
                "Reading init RC files and comparing against AOSP CIL policy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Results ───────────────────────────────────────────────────────────────────

@Composable
private fun ResultsContent(
    result: RcScanResult,
    confirmedDomains: Map<String, Boolean>,
    filterText: String,
    onFilterChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onConfirmAll: (List<SeclabelEntry>) -> Unit,
    onConfirmNone: (List<SeclabelEntry>) -> Unit,
    onGenerate: () -> Unit
) {
    val confirmedCount = confirmedDomains.values.count { it }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Summary card
        item {
            ScanSummaryCard(result = result)
        }

        // Search bar
        item {
            SearchBar(
                query = filterText,
                onQueryChange = onFilterChange,
                placeholder = "Filter by domain name…"
            )
        }

        // Already covered section (collapsed info)
        if (result.alreadyCovered.isNotEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = EnforcingGreen.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ ${result.alreadyCovered.size} domain(s) already in AOSP policy — no action needed",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = EnforcingGreen
                    )
                }
            }
        }

        // UNSAFE blocked warning
        if (result.unsafeEntries.isNotEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = CriticalRed.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✗ ${result.unsafeEntries.size} domain(s) blocked (UNSAFE core system types) — " +
                               result.unsafeEntries.take(3).joinToString { it.domainName },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = CriticalRed
                    )
                }
            }
        }

        // SAFE entries section
        val filteredSafe = result.safeEntries.filter {
            filterText.isBlank() || it.domainName.contains(filterText, ignoreCase = true)
        }
        if (filteredSafe.isNotEmpty()) {
            item {
                SectionHeaderWithActions(
                    title      = "SAFE — Auto-generate (${filteredSafe.size})",
                    color      = EnforcingGreen,
                    onAll      = { onConfirmAll(filteredSafe) },
                    onNone     = { onConfirmNone(filteredSafe) }
                )
            }
            items(filteredSafe, key = { "safe_${it.domainName}" }) { entry ->
                SeclabelEntryCard(
                    entry     = entry,
                    confirmed = confirmedDomains[entry.domainName] ?: true,
                    onToggle  = { onToggle(entry.domainName) }
                )
            }
        }

        // REVIEW entries section
        val filteredReview = result.reviewEntries.filter {
            filterText.isBlank() || it.domainName.contains(filterText, ignoreCase = true)
        }
        if (filteredReview.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeaderWithActions(
                    title  = "REVIEW — Confirm each (${filteredReview.size})",
                    color  = PermissiveAmber,
                    onAll  = { onConfirmAll(filteredReview) },
                    onNone = { onConfirmNone(filteredReview) }
                )
            }
            items(filteredReview, key = { "review_${it.domainName}" }) { entry ->
                SeclabelEntryCard(
                    entry     = entry,
                    confirmed = confirmedDomains[entry.domainName] ?: false,
                    onToggle  = { onToggle(entry.domainName) }
                )
            }
        }

        // Generate button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick  = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                enabled  = confirmedCount > 0
            ) {
                Icon(Icons.Default.Check, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate CIL for $confirmedCount domain(s)")
            }
        }
    }
}

@Composable
private fun ScanSummaryCard(result: RcScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Scan Complete", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem("RC Files", result.scannedRcFiles, OrphanedGrey)
                SummaryItem("Total Domains", result.allEntries.size, OrphanedGrey)
                SummaryItem("Missing", result.missingEntries.size, PermissiveAmber)
                SummaryItem("SAFE", result.safeEntries.size, EnforcingGreen)
                SummaryItem("REVIEW", result.reviewEntries.size, ReviewBlue)
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeaderWithActions(
    title: String,
    color: androidx.compose.ui.graphics.Color,
    onAll: () -> Unit,
    onNone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = color)
        Row {
            TextButton(onClick = onAll)  { Text("All",  style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = onNone) { Text("None", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun SeclabelEntryCard(
    entry: SeclabelEntry,
    confirmed: Boolean,
    onToggle: () -> Unit
) {
    val safetyColor = when (entry.safety) {
        TypeSafety.SAFE   -> EnforcingGreen
        TypeSafety.REVIEW -> PermissiveAmber
        TypeSafety.UNSAFE -> CriticalRed
    }

    OutlinedCard(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (confirmed) Icons.Default.CheckBox
                              else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (confirmed) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        entry.domainName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = safetyColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            entry.safety.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = safetyColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (entry.execPath.isNotBlank()) {
                    Text(
                        entry.execPath,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${entry.partition.dirName} ← ${entry.sourceFile.substringAfterLast("/")}:${entry.lineNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.existsInOem) {
                    Text(
                        "⚠ Not found in OEM CIL either — double-check this domain",
                        style = MaterialTheme.typography.labelSmall,
                        color = PermissiveAmber
                    )
                }
            }
        }
    }
}

// ── Generating ────────────────────────────────────────────────────────────────

@Composable
private fun GeneratingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Text("Generating CIL…", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ── Done ──────────────────────────────────────────────────────────────────────

@Composable
private fun DoneContent(outputDir: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Check, contentDescription = null,
            tint = EnforcingGreen, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("CIL Generated", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = EnforcingGreen)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Output written to:", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                outputDir,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Check INSTRUCTIONS.txt in the output folder for\n" +
            "exact destinations and APPEND vs NEW FILE instructions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Scan Again")
        }
    }
}

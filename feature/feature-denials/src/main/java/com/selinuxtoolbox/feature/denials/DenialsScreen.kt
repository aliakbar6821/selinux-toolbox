package com.selinuxtoolbox.feature.denials

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.domain.analyzer.SafetyConfig
import com.selinuxtoolbox.core.domain.analyzer.TypeSafety
import com.selinuxtoolbox.core.model.DenialGroup
import com.selinuxtoolbox.core.model.FixSuggestion

@Composable
fun DenialsScreen(
    viewModel: DenialsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onLogFilePicked(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Denial Analyzer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val step = uiState.step) {
                is DenialsStep.NoProject -> NoProjectMessage()

                is DenialsStep.Idle -> IdleContent(
                    projectName = uiState.projectName,
                    onPickLog   = { filePicker.launch(arrayOf("text/*", "*/*")) }
                )

                is DenialsStep.Importing,
                is DenialsStep.Analyzing,
                is DenialsStep.Applying -> LoadingContent(step)

                is DenialsStep.Results -> ResultsContent(
                    step        = step,
                    onToggle    = { group -> viewModel.toggleGroupAccepted(group) },
                    onApply     = { viewModel.applyAcceptedFixes() }
                )

                is DenialsStep.Done -> DoneContent(
                    outputPath   = step.outputPath,
                    rulesWritten = step.rulesWritten,
                    onReset      = { viewModel.reset() }
                )

                is DenialsStep.Error -> ErrorContent(
                    message = step.message,
                    onRetry = { viewModel.reset() }
                )
            }
        }
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(projectName: String, onPickLog: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.FileOpen,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Import Boot Log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Import dmesg, logcat, or last_kmsg from a failed boot to find missing SELinux allow rules.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Project: $projectName",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick  = onPickLog,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pick Log File")
        }
    }
}

// ── Loading ───────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent(step: DenialsStep) {
    val message = when (step) {
        is DenialsStep.Importing -> "Importing log file…"
        is DenialsStep.Analyzing -> "Parsing AVC denials…"
        is DenialsStep.Applying  -> "Writing CIL rules…"
        else                     -> "Working…"
    }
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

// ── Results ───────────────────────────────────────────────────────────────────

@Composable
private fun ResultsContent(
    step    : DenialsStep.Results,
    onToggle: (DenialGroup) -> Unit,
    onApply : () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // Summary strip
        SummaryStrip(step)

        // Group list
        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(step.groups, key = { "${it.sourceDomain}::${it.targetType}::${it.objectClass}" }) { group ->
                val groupKey = "${group.sourceDomain}::${group.targetType}::${group.objectClass}"
                DenialGroupCard(
                    group      = group,
                    isAccepted = groupKey in step.acceptedIds,
                    onToggle   = { onToggle(group) }
                )
            }
        }

        // Apply bar
        Surface(shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "${step.acceptedIds.size} groups accepted",
                    style = MaterialTheme.typography.labelLarge
                )
                Button(
                    onClick  = onApply,
                    enabled  = step.acceptedIds.isNotEmpty()
                ) {
                    Text("Apply & Save")
                }
            }
        }
    }
}

@Composable
private fun SummaryStrip(step: DenialsStep.Results) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Log: ${step.logFileName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip("${step.totalDenials} denials",  MaterialTheme.colorScheme.primary)
                SummaryChip("${step.safeCount} safe",        MaterialTheme.colorScheme.tertiary)
                SummaryChip("${step.reviewCount} review",    MaterialTheme.colorScheme.secondary)
                if (step.unsafeCount > 0)
                    SummaryChip("${step.unsafeCount} blocked", MaterialTheme.colorScheme.error)
                if (step.intentionalCount > 0)
                    SummaryChip("${step.intentionalCount} intentional", MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color  = color.copy(alpha = 0.15f),
        shape  = RoundedCornerShape(50)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = color
        )
    }
}

@Composable
private fun DenialGroupCard(
    group     : DenialGroup,
    isAccepted: Boolean,
    onToggle  : () -> Unit
) {
    val safety      = SafetyConfig.classify(group.sourceDomain)
    val isBlocked   = safety == TypeSafety.UNSAFE || group.isIntentional
    val borderColor = when {
        group.isIntentional           -> MaterialTheme.colorScheme.outline
        safety == TypeSafety.UNSAFE   -> MaterialTheme.colorScheme.error
        isAccepted                    -> MaterialTheme.colorScheme.primary
        safety == TypeSafety.REVIEW   -> MaterialTheme.colorScheme.secondary
        else                          -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isBlocked) { onToggle() },
        border = androidx.compose.foundation.BorderStroke(
            width = if (isAccepted) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header row
            Row(
                modifier      = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Accept/reject icon
                Icon(
                    imageVector        = when {
                        group.isIntentional         -> Icons.Default.Info
                        safety == TypeSafety.UNSAFE -> Icons.Default.Block
                        isAccepted                  -> Icons.Default.CheckCircle
                        else                        -> Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint               = borderColor,
                    modifier           = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.sourceDomain,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "→ ${group.targetType}  |  ${group.objectClass}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SafetyBadge(safety, group.isIntentional)
            }

            Spacer(Modifier.height(8.dp))

            // Permissions
            Text(
                "Permissions: ${group.permissions.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(4.dp))
            Text(
                "${group.denials.size} denial(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // CIL rule preview (for SimpleAllow)
            val allowFix = group.suggestions.filterIsInstance<FixSuggestion.SimpleAllow>().firstOrNull()
            if (allowFix != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    shape    = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        allowFix.cilRule.trim(),
                        modifier    = Modifier.padding(8.dp),
                        style       = MaterialTheme.typography.bodySmall,
                        fontFamily  = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    allowFix.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Intentional denial explanation
            val intentFix = group.suggestions.filterIsInstance<FixSuggestion.IntentionalDenial>().firstOrNull()
            if (intentFix != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠ ${intentFix.metadataReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    intentFix.recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Blocked explanation
            val blockedFix = group.suggestions.filterIsInstance<FixSuggestion.NeverAllowConflict>().firstOrNull()
            if (blockedFix != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🚫 ${blockedFix.explanation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SafetyBadge(safety: TypeSafety, isIntentional: Boolean) {
    val (label, color) = when {
        isIntentional               -> "INTENTIONAL" to MaterialTheme.colorScheme.outline
        safety == TypeSafety.SAFE   -> "SAFE"        to MaterialTheme.colorScheme.tertiary
        safety == TypeSafety.REVIEW -> "REVIEW"      to MaterialTheme.colorScheme.secondary
        else                        -> "BLOCKED"     to MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Done ──────────────────────────────────────────────────────────────────────

@Composable
private fun DoneContent(outputPath: String, rulesWritten: Int, onReset: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle, null,
            modifier = Modifier.size(64.dp),
            tint     = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.height(16.dp))
        Text("Done!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("$rulesWritten allow rules written.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                outputPath,
                modifier   = Modifier.padding(12.dp),
                style      = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Open INSTRUCTIONS.txt in that folder for next steps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onReset) { Text("Analyze Another Log") }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) { Text("Go Back") }
    }
}

// ── No project ────────────────────────────────────────────────────────────────

@Composable
private fun NoProjectMessage() {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
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

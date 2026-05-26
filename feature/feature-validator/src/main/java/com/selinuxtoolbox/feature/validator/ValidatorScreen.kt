package com.selinuxtoolbox.feature.validator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.domain.usecase.CilValidationResult
import com.selinuxtoolbox.core.domain.usecase.PreCheckResult
import com.selinuxtoolbox.core.domain.usecase.SecilcError
import com.selinuxtoolbox.core.domain.usecase.SeclabelCheck
import com.selinuxtoolbox.core.domain.usecase.SeclabelStatus

@Composable
fun ValidatorScreen(
    viewModel: ValidatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Validator") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val step = uiState.step) {
                is ValidatorStep.NoProject ->
                    CenteredMessage("No active project. Create or open a project first.")

                is ValidatorStep.Idle ->
                    IdleContent(
                        projectName       = uiState.projectName,
                        onRunCil          = { viewModel.runCilValidation() },
                        onRunSeclabels    = { viewModel.runSeclabelValidation() }
                    )

                is ValidatorStep.Running ->
                    CenteredLoading("Validating…")

                is ValidatorStep.CilResult ->
                    CilResultContent(
                        result       = step.result,
                        filesChecked = step.filesChecked,
                        onBack       = { viewModel.reset() }
                    )

                is ValidatorStep.SeclabelResult ->
                    SeclabelResultContent(
                        result = step.result,
                        onBack = { viewModel.reset() }
                    )

                is ValidatorStep.Error ->
                    CenteredMessage(step.message, isError = true, onBack = { viewModel.reset() })
            }
        }
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(
    projectName: String,
    onRunCil: () -> Unit,
    onRunSeclabels: () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Validator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Project: $projectName",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(32.dp))

        // CIL dry-run card
        ValidatorCard(
            title       = "CIL Dry-Run",
            description = "Run secilc on all CIL files in load order. Catches syntax errors, " +
                "missing type declarations, and neverallow violations before you flash.",
            buttonLabel = "Run secilc",
            onClick     = onRunCil
        )

        Spacer(Modifier.height(16.dp))

        // Seclabel check card
        ValidatorCard(
            title       = "Seclabel Checker",
            description = "Scan all OEM init .rc files and verify every seclabel domain exists " +
                "in the current policy (AOSP + generated). Shows MISSING and SUSPICIOUS entries.",
            buttonLabel = "Check Seclabels",
            onClick     = onRunSeclabels
        )
    }
}

@Composable
private fun ValidatorCard(
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel)
            }
        }
    }
}

// ── CIL Result ────────────────────────────────────────────────────────────────

@Composable
private fun CilResultContent(
    result: CilValidationResult,
    filesChecked: Int,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Pass / Fail banner
        val isPassing = result is CilValidationResult.Pass
        Surface(
            color    = if (isPassing) MaterialTheme.colorScheme.tertiaryContainer
                       else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier          = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = if (isPassing) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint               = if (isPassing) MaterialTheme.colorScheme.tertiary
                                         else MaterialTheme.colorScheme.error,
                    modifier           = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (isPassing) "PASS — Policy compiles cleanly" else "FAIL — Compilation errors found",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = if (isPassing) MaterialTheme.colorScheme.onTertiaryContainer
                                      else MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (result is CilValidationResult.Pass) {
                        Text(
                            "${result.filesChecked} files checked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pre-checks
            val preChecks = when (result) {
                is CilValidationResult.Pass -> result.preChecks
                is CilValidationResult.Fail -> result.preChecks
                else -> emptyList()
            }
            if (preChecks.isNotEmpty()) {
                item {
                    Text(
                        "Pre-checks",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(preChecks) { check -> PreCheckCard(check) }
            }

            // Errors from secilc
            if (result is CilValidationResult.Fail && result.errors.isNotEmpty()) {
                item {
                    Text(
                        "secilc errors (${result.errors.size})",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.error,
                        modifier   = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(result.errors) { error -> SecilcErrorCard(error) }
            }

            // Setup error
            if (result is CilValidationResult.SetupError) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            result.reason,
                            modifier = Modifier.padding(12.dp),
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onErrorContainer
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

@Composable
private fun PreCheckCard(check: PreCheckResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint               = if (check.passed) MaterialTheme.colorScheme.tertiary
                                      else MaterialTheme.colorScheme.secondary,
                modifier           = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(check.fileName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                check.issues.forEach { issue ->
                    Text(issue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun SecilcErrorCard(error: SecilcError) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    error.file,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onErrorContainer
                )
                if (error.line != null) {
                    Text(
                        ":${error.line}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                error.message,
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onErrorContainer,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ── Seclabel Result ───────────────────────────────────────────────────────────

@Composable
private fun SeclabelResultContent(
    result: com.selinuxtoolbox.core.domain.usecase.SeclabelValidationResult,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Summary strip
        Surface(
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier              = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryChip("${result.validCount} valid",       MaterialTheme.colorScheme.tertiary)
                SummaryChip("${result.missingCount} missing",   MaterialTheme.colorScheme.error)
                SummaryChip("${result.suspiciousCount} suspicious", MaterialTheme.colorScheme.secondary)
            }
        }

        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(result.checks, key = { "${it.serviceName}_${it.rcFile}" }) { check ->
                SeclabelCheckCard(check)
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

@Composable
private fun SeclabelCheckCard(check: SeclabelCheck) {
    val (icon, tint) = when (check.status) {
        SeclabelStatus.VALID      -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.tertiary
        SeclabelStatus.MISSING    -> Icons.Default.Error       to MaterialTheme.colorScheme.error
        SeclabelStatus.SUSPICIOUS -> Icons.Default.Warning     to MaterialTheme.colorScheme.secondary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(check.serviceName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Domain: ${check.domain}",
                    style      = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "RC: ${check.rcFile}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (check.execPath != null) {
                    Text(
                        "Exec: ${check.execPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    check.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint
                )
            }
        }
    }
}

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

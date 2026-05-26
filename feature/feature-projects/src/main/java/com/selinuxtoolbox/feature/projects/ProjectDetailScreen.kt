package com.selinuxtoolbox.feature.projects

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.NavController
import com.selinuxtoolbox.core.model.ActionRecord
import com.selinuxtoolbox.core.model.ActionType
import com.selinuxtoolbox.core.model.ActionValidity
import com.selinuxtoolbox.core.model.ActionValidation
import com.selinuxtoolbox.core.model.Project
import com.selinuxtoolbox.core.ui.theme.CriticalRed
import com.selinuxtoolbox.core.ui.theme.EnforcingGreen
import com.selinuxtoolbox.core.ui.theme.OrphanedGrey
import com.selinuxtoolbox.core.ui.theme.ReviewBlue
import com.selinuxtoolbox.core.ui.theme.WarningYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    navController: NavController,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val project = uiState.projects.firstOrNull { it.id == projectId }

    LaunchedEffect(project) {
        project?.let { viewModel.loadProjectDetail(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectsEvent.ExportSuccess ->
                    snackbarHostState.showSnackbar("Exported: ${event.zipPath}")
                is ProjectsEvent.Error ->
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(project?.name ?: "Project Detail", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onExportProject(projectId) }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (project == null || detailState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ProjectSummaryCard(project = project) }

            item {
                ActionValidationSection(
                    validations = detailState.validations,
                    isLoading = detailState.isLoadingValidations,
                    onValidate = { viewModel.validateProjectActions(projectId) }
                )
            }

            item {
                Text(
                    "Action Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (detailState.actions.isEmpty()) {
                item {
                    Text(
                        "No actions recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(
                    items = detailState.actions.sortedByDescending { it.timestamp },
                    key = { it.id }
                ) { action ->
                    ActionLogCard(action = action)
                }
            }
        }
    }
}

@Composable
private fun ProjectSummaryCard(project: Project) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Project Details",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow("Source", "${project.sourceDevice} (${project.sourceRom})")
            DetailRow("Target", "${project.targetDevice} (${project.targetRom})")
            DetailRow("Folder", project.projectFolderPath)
            DetailRow("Created", formatDate(project.createdAt))
            DetailRow("Status", project.status.name)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (label == "Folder") FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.65f)
        )
    }
}

@Composable
private fun ActionValidationSection(
    validations: List<ActionValidation>,
    isLoading: Boolean,
    onValidate: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Post-Restore Validation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onValidate, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Validate")
                    }
                }
            }
            Text(
                "After factory reset + reimport: check which actions still need to be applied",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (validations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                validations.forEach { validation ->
                    ValidationRow(validation = validation)
                }
            }
        }
    }
}

@Composable
private fun ValidationRow(validation: ActionValidation) {
    val (icon, color) = when (validation.validity) {
        ActionValidity.ALREADY_APPLIED -> Icons.Default.CheckCircle to EnforcingGreen
        ActionValidity.NEEDS_REAPPLY   -> Icons.Default.Refresh to ReviewBlue
        ActionValidity.PARTIALLY_APPLICABLE -> Icons.Default.Warning to WarningYellow
        ActionValidity.NOT_APPLICABLE  -> Icons.Default.Error to CriticalRed
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = validation.action.description,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = validation.explanation,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionLogCard(action: ActionRecord) {
    val typeColor = when (action.type) {
        ActionType.CLEANUP_PASS    -> EnforcingGreen
        ActionType.DENIAL_RULE_ADD -> ReviewBlue
        ActionType.SECLABEL_FIX   -> WarningYellow
        ActionType.CONFLICT_RESOLVE -> CriticalRed
        ActionType.COMPILE         -> MaterialTheme.colorScheme.primary
        ActionType.CONTEXT_UPDATE  -> OrphanedGrey
        ActionType.MANUAL_EDIT     -> OrphanedGrey
        ActionType.RESTORE         -> ReviewBlue
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = action.type.name.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium,
                    color = typeColor,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (action.undone) {
                        Text(
                            "UNDONE",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrphanedGrey
                        )
                    }
                    Text(
                        formatDate(action.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodySmall
            )
            if (action.changedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${action.changedFiles.size} file(s) changed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(timestamp))

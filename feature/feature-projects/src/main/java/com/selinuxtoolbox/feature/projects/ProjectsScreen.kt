package com.selinuxtoolbox.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selinuxtoolbox.core.model.ActiveMode
import com.selinuxtoolbox.core.model.Project
import com.selinuxtoolbox.core.model.ProjectStatus

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel = hiltViewModel(),
    onProjectSelected: (Long) -> Unit = {}
) {
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectsEvent.ProjectCreated -> {
                    showCreateSheet = false
                    snackbarHostState.showSnackbar("Project created")
                    onProjectSelected(event.projectId)
                }
                is ProjectsEvent.ProjectDeleted ->
                    snackbarHostState.showSnackbar("Deleted: ${event.name}")
                is ProjectsEvent.ActiveProjectSet ->
                    snackbarHostState.showSnackbar("Active project set")
                is ProjectsEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Projects") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "New project")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.projects.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.FolderOpen, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "No projects yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Tap + to create your first project",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.projects, key = { it.id }) { project ->
                    ProjectCard(
                        project  = project,
                        onSelect = { viewModel.onSetActiveProject(project.id); onProjectSelected(project.id) },
                        onDelete = { viewModel.onDeleteProject(project) }
                    )
                }
            }
        }
    }

    // ── Create project bottom sheet ───────────────────────────────────────────
    if (showCreateSheet) {
        CreateProjectSheet(
            formState = formState,
            onDismiss = { showCreateSheet = false; viewModel.resetForm() },
            onNameChange         = viewModel::onNameChange,
            onSourceDeviceChange = viewModel::onSourceDeviceChange,
            onTargetDeviceChange = viewModel::onTargetDeviceChange,
            onSourceRomChange    = viewModel::onSourceRomChange,
            onTargetRomChange    = viewModel::onTargetRomChange,
            onRequestCreate      = viewModel::onRequestCreate
        )
    }

    // ── Mode picker dialog ────────────────────────────────────────────────────
    if (formState.showModeDialog) {
        ModeSelectorDialog(
            projectName = formState.name,
            onOffline   = { viewModel.onModeSelected(ActiveMode.OFFLINE) },
            onLive      = { viewModel.onModeSelected(ActiveMode.LIVE) },
            onDismiss   = { viewModel.onModeDismissed() }
        )
    }
}

// ── Project card ──────────────────────────────────────────────────────────────

@Composable
private fun ProjectCard(
    project: Project,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        project.name,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    ModeChip(project.activeMode)
                }
                Spacer(Modifier.height(4.dp))
                if (project.sourceDevice.isNotBlank() || project.targetDevice.isNotBlank()) {
                    Text(
                        "${project.sourceDevice} → ${project.targetDevice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (project.oemPath.isNotBlank()) {
                    Text(
                        "OEM: ${project.oemPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Delete project?") },
            text    = { Text("This removes the project record. Files on SD card are not deleted.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModeChip(mode: ActiveMode) {
    val (label, color) = when (mode) {
        ActiveMode.OFFLINE -> "OFFLINE" to MaterialTheme.colorScheme.secondary
        ActiveMode.LIVE    -> "LIVE"    to MaterialTheme.colorScheme.tertiary
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Create sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateProjectSheet(
    formState: CreateProjectFormState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onSourceDeviceChange: (String) -> Unit,
    onTargetDeviceChange: (String) -> Unit,
    onSourceRomChange: (String) -> Unit,
    onTargetRomChange: (String) -> Unit,
    onRequestCreate: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "New Project",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value         = formState.name,
                onValueChange = onNameChange,
                label         = { Text("Project name *") },
                isError       = formState.nameError != null,
                supportingText = formState.nameError?.let { { Text(it) } },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value         = formState.sourceDevice,
                onValueChange = onSourceDeviceChange,
                label         = { Text("OEM device (e.g. OnePlus ACE2V)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value         = formState.targetDevice,
                onValueChange = onTargetDeviceChange,
                label         = { Text("AOSP device (e.g. Moto G54)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value         = formState.sourceRom,
                onValueChange = onSourceRomChange,
                label         = { Text("OEM ROM (e.g. ColorOS 14)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value         = formState.targetRom,
                onValueChange = onTargetRomChange,
                label         = { Text("AOSP ROM (e.g. LineageOS 21)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = onRequestCreate,
                enabled  = !formState.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Project")
                }
            }
        }
    }
}

// ── Mode selector dialog ──────────────────────────────────────────────────────

@Composable
private fun ModeSelectorDialog(
    projectName: String,
    onOffline: () -> Unit,
    onLive: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose project mode") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "\"$projectName\"",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // OFFLINE card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOffline() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOff, null)
                            Spacer(Modifier.width(8.dp))
                            Text("OFFLINE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Device is not fully booted. Folders will be created on SD card:\n" +
                            "  OEM/  AOSP/  work/  logs/\n" +
                            "Fill OEM/ and AOSP/ with your selinux files.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // LIVE card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLive() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, null)
                            Spacer(Modifier.width(8.dp))
                            Text("LIVE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Device is booted with root access. App reads\n" +
                            "directly from live partitions via root shell.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

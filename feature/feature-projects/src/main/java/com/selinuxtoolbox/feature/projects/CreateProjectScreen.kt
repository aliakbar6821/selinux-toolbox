package com.selinuxtoolbox.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.selinuxtoolbox.core.model.ActiveMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    navController: NavController,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectsEvent.ProjectCreated -> navController.popBackStack()
                is ProjectsEvent.Error ->
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Project") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetForm()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Project Info",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value         = formState.name,
                onValueChange = viewModel::onNameChange,
                label         = { Text("Project Name *") },
                placeholder   = { Text("e.g. ColorOS") },
                isError       = formState.nameError != null,
                supportingText = { formState.nameError?.let { Text(it) } },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Source (donor ROM)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value         = formState.sourceDevice,
                onValueChange = viewModel::onSourceDeviceChange,
                label         = { Text("Source Device") },
                placeholder   = { Text("e.g. OnePlus ACE2V") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            OutlinedTextField(
                value         = formState.sourceRom,
                onValueChange = viewModel::onSourceRomChange,
                label         = { Text("Source ROM") },
                placeholder   = { Text("e.g. ColorOS 14") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Target (device being ported to)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value         = formState.targetDevice,
                onValueChange = viewModel::onTargetDeviceChange,
                label         = { Text("Target Device") },
                placeholder   = { Text("e.g. Moto G54") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            OutlinedTextField(
                value         = formState.targetRom,
                onValueChange = viewModel::onTargetRomChange,
                label         = { Text("Target ROM") },
                placeholder   = { Text("e.g. LineageOS 21") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                // ✅ was submitCreateProject (private) — now onRequestCreate (public)
                onClick  = viewModel::onRequestCreate,
                modifier = Modifier.fillMaxWidth(),
                enabled  = !formState.isSubmitting
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier    = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Project")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Mode dialog — shown on top of this screen after name is validated
    if (formState.showModeDialog) {
        ModeDialog(
            projectName = formState.name,
            onOffline   = { viewModel.onModeSelected(ActiveMode.OFFLINE) },
            onLive      = { viewModel.onModeSelected(ActiveMode.LIVE) },
            onDismiss   = { viewModel.onModeDismissed() }
        )
    }
}

@Composable
private fun ModeDialog(
    projectName: String,
    onOffline: () -> Unit,
    onLive: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose project mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "\"$projectName\"",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOffline() },
                    colors   = CardDefaults.cardColors(
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
                            "Device not fully booted. Creates folders on SD card:\n" +
                            "  OEM/  AOSP/  work/  logs/\n" +
                            "Each with system/ system_ext/ product/ vendor/ odm/\n" +
                            "and selinux/ + init/ inside each partition.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onLive() },
                    colors   = CardDefaults.cardColors(
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
                            "Device is booted with root. App reads live\n" +
                            "partitions directly via root shell.",
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

package com.selinuxtoolbox.feature.dashboard

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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.selinuxtoolbox.core.model.AvcDenial
import com.selinuxtoolbox.core.model.Project
import com.selinuxtoolbox.core.model.SelinuxMode
import com.selinuxtoolbox.core.ui.theme.CriticalRed
import com.selinuxtoolbox.core.ui.theme.DisabledRed
import com.selinuxtoolbox.core.ui.theme.EnforcingGreen
import com.selinuxtoolbox.core.ui.theme.PermissiveAmber
import com.selinuxtoolbox.core.ui.theme.ReviewBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToProjects: () -> Unit,
    onNavigateToDenials: () -> Unit,
    onNavigateToContextDiff: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DashboardEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
                is DashboardEvent.ModeChanged -> {
                    val label = if (event.enforcing) "Enforcing" else "Permissive"
                    snackbarHostState.showSnackbar("SELinux set to $label")
                }
                DashboardEvent.StreamStarted ->
                    snackbarHostState.showSnackbar("Live denial stream started")
                DashboardEvent.StreamStopped ->
                    snackbarHostState.showSnackbar("Live denial stream stopped")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SELinux Toolbox",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SELinux status card
            item {
                SelinuxStatusCard(
                    status = uiState.selinuxStatus,
                    isLoading = uiState.isLoadingStatus,
                    onToggleMode = { viewModel.toggleSelinuxMode() }
                )
            }

            // Active project card
            item {
                ActiveProjectCard(
                    project = uiState.activeProject,
                    onNavigateToProjects = onNavigateToProjects
                )
            }

            // Quick actions
            item {
                QuickActionsRow(
                    onNavigateToDenials = onNavigateToDenials,
                    onNavigateToProjects = onNavigateToProjects,
                    onNavigateToContextDiff = onNavigateToContextDiff
                )
            }

            // Denial stream controls + list header
            item {
                DenialSectionHeader(
                    isStreaming = uiState.isStreaming,
                    streamedCount = uiState.streamedDenialCount,
                    denialCount = uiState.recentDenials.size,
                    isLoading = uiState.isLoadingDenials,
                    onStartStream = { viewModel.startStreaming() },
                    onStopStream = { viewModel.stopStreaming() },
                    onClear = { viewModel.clearDenials() }
                )
            }

            if (uiState.isLoadingDenials && uiState.recentDenials.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            } else if (uiState.recentDenials.isEmpty()) {
                item {
                    EmptyDenialsPlaceholder(isStreaming = uiState.isStreaming)
                }
            } else {
                items(
                    items = uiState.recentDenials,
                    key = { it.id }
                ) { denial ->
                    DenialRow(denial = denial)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SELinux Status Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelinuxStatusCard(
    status: com.selinuxtoolbox.core.domain.usecase.SelinuxStatus?,
    isLoading: Boolean,
    onToggleMode: () -> Unit
) {
    val mode = status?.mode ?: SelinuxMode.UNKNOWN
    val (modeLabel, modeColor) = when (mode) {
        SelinuxMode.ENFORCING  -> "Enforcing"  to EnforcingGreen
        SelinuxMode.PERMISSIVE -> "Permissive" to PermissiveAmber
        SelinuxMode.DISABLED   -> "Disabled"   to DisabledRed
        SelinuxMode.UNKNOWN    -> "Unknown"    to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = modeColor.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = modeColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "SELinux",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                text = modeLabel,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = modeColor
                            )
                        }
                    }
                }

                // Toggle button — only show when mode is known
                if (mode != SelinuxMode.UNKNOWN && mode != SelinuxMode.DISABLED) {
                    val toggleLabel = if (mode == SelinuxMode.ENFORCING) "Set Permissive"
                                      else "Set Enforcing"
                    val toggleColor = if (mode == SelinuxMode.ENFORCING) PermissiveAmber
                                      else EnforcingGreen
                    FilledTonalButton(
                        onClick = onToggleMode,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = toggleColor.copy(alpha = 0.15f),
                            contentColor = toggleColor
                        )
                    ) {
                        Text(toggleLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Device info row
            status?.let { s ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = modeColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DeviceInfoChip(label = "Device", value = s.deviceModel)
                    DeviceInfoChip(label = "Android", value = s.androidVersion)
                    DeviceInfoChip(label = "Vendor API", value = s.vendorApiLevel.toString())
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active Project Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveProjectCard(
    project: Project?,
    onNavigateToProjects: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = if (project != null) ReviewBlue
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active Project",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (project != null) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.sourceDevice} → ${project.targetDevice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "No active project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FilledTonalButton(onClick = onNavigateToProjects) {
                Text(if (project != null) "Switch" else "Open")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick Actions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickActionsRow(
    onNavigateToDenials: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToContextDiff: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.BugReport,
            label = "Analyze\nDenials",
            onClick = onNavigateToDenials
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CompareArrows,
            label = "Context\nDiff",
            onClick = onNavigateToContextDiff
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CleaningServices,
            label = "Cleanup\nPolicy",
            onClick = onNavigateToProjects
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.FolderOpen,
            label = "Projects",
            onClick = onNavigateToProjects
        )
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Denial Section Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DenialSectionHeader(
    isStreaming: Boolean,
    streamedCount: Int,
    denialCount: Int,
    isLoading: Boolean,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onClear: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "AVC Denials",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (denialCount > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = CriticalRed.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$denialCount",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = CriticalRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (isStreaming) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = "Live",
                        tint = CriticalRed,
                        modifier = Modifier.size(8.dp)
                    )
                    Text(
                        "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = CriticalRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (denialCount > 0) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.ClearAll,
                            contentDescription = "Clear",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (isStreaming) {
                    FilledTonalButton(
                        onClick = onStopStream,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CriticalRed.copy(alpha = 0.15f),
                            contentColor = CriticalRed
                        )
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    FilledTonalButton(onClick = onStartStream) {
                        Icon(
                            Icons.Default.Stream,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Live", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (isStreaming) {
            Text(
                text = "Streaming dmesg — $streamedCount denial(s) captured",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Recent denials from dmesg (snapshot)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Denial Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DenialRow(denial: AvcDenial) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Source → Target
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${denial.sourceDomain} → ${denial.targetType}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${denial.objectClass} : ${denial.permission}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = CriticalRed
                    )
                }
                // comm / pid
                denial.comm?.let { comm ->
                    Text(
                        text = comm,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            // path if available
            denial.path?.let { path ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyDenialsPlaceholder(isStreaming: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (isStreaming) "Waiting for AVC denials…"
                       else "No recent AVC denials found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isStreaming) {
                Text(
                    text = "Tap Live to stream dmesg in real time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

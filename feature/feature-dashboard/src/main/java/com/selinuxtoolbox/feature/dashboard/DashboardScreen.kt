package com.selinuxtoolbox.feature.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    onNavigateToProjects: () -> Unit,
    onNavigateToDenials: () -> Unit,
    onNavigateToContextDiff: () -> Unit,
    onNavigateToRcScan: () -> Unit,
    onNavigateToAttributes: () -> Unit,
    onNavigateToValidator: () -> Unit,
    onNavigateToCompile: () -> Unit,
    onNavigateToExplorer: () -> Unit,
    onNavigateToLogImporter: () -> Unit,
    onNavigateToCleanup: () -> Unit,
    onNavigateToDiff: () -> Unit,
    onNavigateToConflicts: () -> Unit,
    onNavigateToContexts: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Drawer state
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                onNavigateToProjects = onNavigateToProjects,
                onNavigateToDenials = onNavigateToDenials,
                onNavigateToContextDiff = onNavigateToContextDiff,
                onNavigateToRcScan = onNavigateToRcScan,
                onNavigateToAttributes = onNavigateToAttributes,
                onNavigateToValidator = onNavigateToValidator,
                onNavigateToCompile = onNavigateToCompile,
                onNavigateToExplorer = onNavigateToExplorer,
                onNavigateToLogImporter = onNavigateToLogImporter,
                onNavigateToCleanup = onNavigateToCleanup,
                onNavigateToDiff = onNavigateToDiff,
                onNavigateToConflicts = onNavigateToConflicts,
                onNavigateToContexts = onNavigateToContexts,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
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
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open()
                                    else drawerState.close()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation drawer")
                        }
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

                // Quick Actions Grid
                item {
                    QuickActionsGrid(
                        onNavigateToDenials = onNavigateToDenials,
                        onNavigateToContextDiff = onNavigateToContextDiff,
                        onNavigateToCleanup = onNavigateToCleanup,
                        onNavigateToProjects = onNavigateToProjects,
                        onNavigateToRcScan = onNavigateToRcScan,
                        onNavigateToAttributes = onNavigateToAttributes,
                        onNavigateToValidator = onNavigateToValidator,
                        onNavigateToCompile = onNavigateToCompile,
                        onNavigateToExplorer = onNavigateToExplorer,
                        onNavigateToLogImporter = onNavigateToLogImporter,
                        onNavigateToDiff = onNavigateToDiff,
                        onNavigateToConflicts = onNavigateToConflicts
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
}

@Composable
private fun NavigationDrawerContent(
    onNavigateToProjects: () -> Unit,
    onNavigateToDenials: () -> Unit,
    onNavigateToContextDiff: () -> Unit,
    onNavigateToRcScan: () -> Unit,
    onNavigateToAttributes: () -> Unit,
    onNavigateToValidator: () -> Unit,
    onNavigateToCompile: () -> Unit,
    onNavigateToExplorer: () -> Unit,
    onNavigateToLogImporter: () -> Unit,
    onNavigateToCleanup: () -> Unit,
    onNavigateToDiff: () -> Unit,
    onNavigateToConflicts: () -> Unit,
    onNavigateToContexts: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "SELinux Toolbox",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                NavigationDrawerItem(
                    label = { Text("Dashboard") },
                    selected = false,
                    onClick = onCloseDrawer,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Projects") },
                    selected = false,
                    onClick = { onNavigateToProjects(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("RC Seclabel Scanner") },
                    selected = false,
                    onClick = { onNavigateToRcScan(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Context Diff & Generator") },
                    selected = false,
                    onClick = { onNavigateToContextDiff(); onCloseDrawer() },
                    icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Missing Typeattribute Fixer") },
                    selected = false,
                    onClick = { onNavigateToAttributes(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.Category, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Denial Analyzer") },
                    selected = false,
                    onClick = { onNavigateToDenials(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Log Importer") },
                    selected = false,
                    onClick = { onNavigateToLogImporter(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Manual Type Search") },
                    selected = false,
                    onClick = { onNavigateToExplorer(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Policy Cleanup") },
                    selected = false,
                    onClick = { onNavigateToCleanup(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Policy Diff") },
                    selected = false,
                    onClick = { onNavigateToDiff(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.Difference, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Conflict Detector") },
                    selected = false,
                    onClick = { onNavigateToConflicts(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Context Viewer") },
                    selected = false,
                    onClick = { onNavigateToContexts(); onCloseDrawer() },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Validator") },
                    selected = false,
                    onClick = { onNavigateToValidator(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.Verified, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Compile Policy") },
                    selected = false,
                    onClick = { onNavigateToCompile(); onCloseDrawer() },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickActionsGrid(
    onNavigateToDenials: () -> Unit,
    onNavigateToContextDiff: () -> Unit,
    onNavigateToCleanup: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToRcScan: () -> Unit,
    onNavigateToAttributes: () -> Unit,
    onNavigateToValidator: () -> Unit,
    onNavigateToCompile: () -> Unit,
    onNavigateToExplorer: () -> Unit,
    onNavigateToLogImporter: () -> Unit,
    onNavigateToDiff: () -> Unit,
    onNavigateToConflicts: () -> Unit
) {
    data class QuickAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

    val actions = listOf(
        QuickAction(Icons.Default.BugReport, "Analyze Denials", onNavigateToDenials),
        QuickAction(Icons.AutoMirrored.Filled.CompareArrows, "Context Diff", onNavigateToContextDiff),
        QuickAction(Icons.Default.CleaningServices, "Cleanup Policy", onNavigateToCleanup),
        QuickAction(Icons.Default.FolderOpen, "Projects", onNavigateToProjects),
        QuickAction(Icons.Default.Search, "RC Scanner", onNavigateToRcScan),
        QuickAction(Icons.Default.Category, "Attributes Fixer", onNavigateToAttributes),
        QuickAction(Icons.Default.Verified, "Validator", onNavigateToValidator),
        QuickAction(Icons.Default.Build, "Compile Policy", onNavigateToCompile),
        QuickAction(Icons.Default.Search, "Manual Search", onNavigateToExplorer),
        QuickAction(Icons.Default.FileOpen, "Log Importer", onNavigateToLogImporter),
        QuickAction(Icons.Default.Difference, "Policy Diff", onNavigateToDiff),
        QuickAction(Icons.Default.Warning, "Conflict Detector", onNavigateToConflicts)
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 130.dp), // increased from 105dp
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp) // increased from 280dp
    ) {
        items(actions, key = { it.label }) { action ->
            Card(
                onClick = action.onClick,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp // increased from 14sp
                    )
                }
            }
        }
    }
}

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

package com.selinuxtoolbox.feature.offline.rcscanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RcScannerScreen(
    onNavigateBack: () -> Unit,
    viewModel: RcScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RC Seclabel Scanner") },
                actions = {
                    IconButton(onClick = { viewModel.scan() }) {
                        Text("↻")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                when (uiState) {
                    is RcScannerUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
                    }
                    is RcScannerUiState.Error -> {
                        Text(
                            text = (uiState as RcScannerUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    is RcScannerUiState.Success -> {
                        val state = uiState as RcScannerUiState.Success
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(state.rcFileCount.toString(), style = MaterialTheme.typography.titleLarge)
                                    Text("RC Files", style = MaterialTheme.typography.labelMedium)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(state.totalDomains.toString(), style = MaterialTheme.typography.titleLarge)
                                    Text("Total Domains", style = MaterialTheme.typography.labelMedium)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(state.missingDomains.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                                    Text("Missing", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            if (uiState is RcScannerUiState.Success) {
                val domains = (uiState as RcScannerUiState.Success).domains

                item {
                    Text(
                        text = "SAFE — Auto-generate",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                items(domains.filter { it.classification == "SAFE" }) { domain ->
                    DomainCard(domain)
                }

                item {
                    Text(
                        text = "REVIEW — Confirm each",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                items(domains.filter { it.classification == "REVIEW" }) { domain ->
                    DomainCard(domain)
                }
            }
        }
    }
}

@Composable
fun DomainCard(domain: DomainItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(domain.name, style = MaterialTheme.typography.titleMedium)
            Text(domain.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("From: ${domain.sourceFile}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

data class DomainItem(
    val name: String,
    val path: String,
    val sourceFile: String,
    val classification: String
)

sealed class RcScannerUiState {
    object Idle : RcScannerUiState()
    object Loading : RcScannerUiState()
    data class Success(
        val rcFileCount: Int,
        val totalDomains: Int,
        val missingDomains: Int,
        val domains: List<DomainItem>
    ) : RcScannerUiState()
    data class Error(val message: String) : RcScannerUiState()
}

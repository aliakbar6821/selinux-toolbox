package com.selinuxtoolbox.feature.dashboard

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val DASHBOARD_ROUTE = "dashboard"

fun NavController.navigateToDashboard() = navigate(DASHBOARD_ROUTE) {
    popUpTo(DASHBOARD_ROUTE) { inclusive = true }
}

fun NavGraphBuilder.dashboardScreen(
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
    onNavigateToContexts: () -> Unit
) {
    composable(DASHBOARD_ROUTE) {
        DashboardScreen(
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
            onNavigateToContexts = onNavigateToContexts
        )
    }
}

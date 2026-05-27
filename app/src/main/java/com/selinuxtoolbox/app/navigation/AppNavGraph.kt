package com.selinuxtoolbox.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.selinuxtoolbox.feature.attributes.ATTRIBUTES_ROUTE
import com.selinuxtoolbox.feature.attributes.attributesScreen
import com.selinuxtoolbox.feature.attributes.navigateToAttributes
import com.selinuxtoolbox.feature.cleanup.CLEANUP_ROUTE
import com.selinuxtoolbox.feature.cleanup.cleanupScreen
import com.selinuxtoolbox.feature.cleanup.navigateToCleanup
import com.selinuxtoolbox.feature.compile.COMPILE_ROUTE
import com.selinuxtoolbox.feature.compile.compileScreen
import com.selinuxtoolbox.feature.compile.navigateToCompile
import com.selinuxtoolbox.feature.conflicts.CONFLICTS_ROUTE
import com.selinuxtoolbox.feature.conflicts.conflictsScreen
import com.selinuxtoolbox.feature.conflicts.navigateToConflicts
import com.selinuxtoolbox.feature.contexts.CONTEXTS_ROUTE
import com.selinuxtoolbox.feature.contexts.contextsScreen
import com.selinuxtoolbox.feature.contexts.navigateToContexts
import com.selinuxtoolbox.feature.contextdiff.CONTEXT_DIFF_ROUTE
import com.selinuxtoolbox.feature.contextdiff.contextDiffScreen
import com.selinuxtoolbox.feature.contextdiff.navigateToContextDiff
import com.selinuxtoolbox.feature.dashboard.DASHBOARD_ROUTE
import com.selinuxtoolbox.feature.dashboard.dashboardScreen
import com.selinuxtoolbox.feature.denials.DENIALS_ROUTE
import com.selinuxtoolbox.feature.denials.denialsScreen
import com.selinuxtoolbox.feature.denials.navigateToDenials
import com.selinuxtoolbox.feature.diff.DIFF_ROUTE
import com.selinuxtoolbox.feature.diff.diffScreen
import com.selinuxtoolbox.feature.diff.navigateToDiff
import com.selinuxtoolbox.feature.explorer.EXPLORER_ROUTE
import com.selinuxtoolbox.feature.explorer.explorerScreen
import com.selinuxtoolbox.feature.explorer.navigateToExplorer
import com.selinuxtoolbox.feature.logimporter.LOG_IMPORTER_ROUTE
import com.selinuxtoolbox.feature.logimporter.logImporterScreen
import com.selinuxtoolbox.feature.logimporter.navigateToLogImporter
import com.selinuxtoolbox.feature.offline.RC_SCAN_ROUTE
import com.selinuxtoolbox.feature.offline.navigateToRcScan
import com.selinuxtoolbox.feature.offline.rcScanScreen
import com.selinuxtoolbox.feature.projects.navigateToProjects
import com.selinuxtoolbox.feature.projects.projectsGraph
import com.selinuxtoolbox.feature.validator.VALIDATOR_ROUTE
import com.selinuxtoolbox.feature.validator.navigateToValidator
import com.selinuxtoolbox.feature.validator.validatorScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = DASHBOARD_ROUTE
    ) {
        dashboardScreen(
            onNavigateToProjects = { navController.navigateToProjects() },
            onNavigateToDenials  = { navController.navigateToDenials() },
            onNavigateToContextDiff = { navController.navigateToContextDiff() },
            onNavigateToRcScan = { navController.navigateToRcScan() },
            onNavigateToAttributes = { navController.navigateToAttributes() },
            onNavigateToValidator = { navController.navigateToValidator() },
            onNavigateToCompile = { navController.navigateToCompile() },
            onNavigateToExplorer = { navController.navigateToExplorer() },
            onNavigateToLogImporter = { navController.navigateToLogImporter() },
            onNavigateToCleanup = { navController.navigateToCleanup() },
            onNavigateToDiff = { navController.navigateToDiff() },
            onNavigateToConflicts = { navController.navigateToConflicts() },
            onNavigateToContexts = { navController.navigateToContexts() }
        )

        projectsGraph(navController = navController)

        cleanupScreen()

        rcScanScreen()

        denialsScreen()

        validatorScreen()

        compileScreen()

        diffScreen()

        explorerScreen()

        contextsScreen()

        conflictsScreen()

        contextDiffScreen()

        attributesScreen()

        logImporterScreen()
    }
}

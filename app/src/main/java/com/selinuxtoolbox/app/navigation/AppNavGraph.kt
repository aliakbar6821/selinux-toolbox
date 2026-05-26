package com.selinuxtoolbox.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.selinuxtoolbox.feature.attributes.ATTRIBUTES_ROUTE
import com.selinuxtoolbox.feature.attributes.attributesScreen
import com.selinuxtoolbox.feature.cleanup.cleanupScreen
import com.selinuxtoolbox.feature.compile.compileScreen
import com.selinuxtoolbox.feature.conflicts.CONFLICTS_ROUTE
import com.selinuxtoolbox.feature.conflicts.conflictsScreen
import com.selinuxtoolbox.feature.contexts.CONTEXTS_ROUTE
import com.selinuxtoolbox.feature.contexts.contextsScreen
import com.selinuxtoolbox.feature.contextdiff.CONTEXT_DIFF_ROUTE
import com.selinuxtoolbox.feature.contextdiff.contextDiffScreen
import com.selinuxtoolbox.feature.contextdiff.navigateToContextDiff
import com.selinuxtoolbox.feature.dashboard.DASHBOARD_ROUTE
import com.selinuxtoolbox.feature.dashboard.dashboardScreen
import com.selinuxtoolbox.feature.denials.denialsScreen
import com.selinuxtoolbox.feature.denials.navigateToDenials
import com.selinuxtoolbox.feature.diff.diffScreen
import com.selinuxtoolbox.feature.explorer.EXPLORER_ROUTE
import com.selinuxtoolbox.feature.explorer.explorerScreen
import com.selinuxtoolbox.feature.offline.rcScanScreen
import com.selinuxtoolbox.feature.projects.navigateToProjects
import com.selinuxtoolbox.feature.projects.projectsGraph
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
            onNavigateToContextDiff = { navController.navigateToContextDiff() }
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
    }
}

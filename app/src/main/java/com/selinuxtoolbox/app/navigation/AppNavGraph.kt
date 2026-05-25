package com.selinuxtoolbox.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.selinuxtoolbox.feature.cleanup.CLEANUP_ROUTE
import com.selinuxtoolbox.feature.cleanup.cleanupScreen
import com.selinuxtoolbox.feature.dashboard.DASHBOARD_ROUTE
import com.selinuxtoolbox.feature.dashboard.dashboardScreen
import com.selinuxtoolbox.feature.denials.DENIALS_ROUTE
import com.selinuxtoolbox.feature.denials.denialsScreen
import com.selinuxtoolbox.feature.denials.navigateToDenials
import com.selinuxtoolbox.feature.offline.RC_SCAN_ROUTE
import com.selinuxtoolbox.feature.offline.rcScanScreen
import com.selinuxtoolbox.feature.projects.PROJECTS_ROUTE
import com.selinuxtoolbox.feature.projects.navigateToProjects
import com.selinuxtoolbox.feature.projects.projectsGraph

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = DASHBOARD_ROUTE
    ) {
        dashboardScreen(
            onNavigateToProjects = { navController.navigateToProjects() },
            onNavigateToDenials  = { navController.navigateToDenials() }
        )

        projectsGraph(navController = navController)

        cleanupScreen()

        rcScanScreen()

        denialsScreen()

        composable("contexts") {
            androidx.compose.material3.Text("Contexts — coming in Step 5")
        }
        composable("validator") {
            androidx.compose.material3.Text("Validator — coming in Step 6")
        }
        composable("conflicts") {
            androidx.compose.material3.Text("Conflicts — coming in Step 7")
        }
        composable("explorer") {
            androidx.compose.material3.Text("Explorer — coming in Step 8")
        }
        composable("diff") {
            androidx.compose.material3.Text("Diff — coming in Step 9")
        }
        composable("compile") {
            androidx.compose.material3.Text("Compile — coming in Step 10")
        }
    }
}

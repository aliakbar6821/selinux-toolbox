package com.selinuxtoolbox.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.selinuxtoolbox.feature.dashboard.DASHBOARD_ROUTE
import com.selinuxtoolbox.feature.dashboard.dashboardScreen
import com.selinuxtoolbox.feature.projects.PROJECTS_ROUTE
import com.selinuxtoolbox.feature.projects.navigateToProjects
import com.selinuxtoolbox.feature.projects.projectsGraph

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = DASHBOARD_ROUTE
    ) {
        // Step 6 — Dashboard (start destination)
        dashboardScreen(
            onNavigateToProjects = { navController.navigateToProjects() },
            onNavigateToDenials  = { navController.navigate("denials") }
        )

        // Step 5 — Projects
        projectsGraph(navController = navController)

        // Remaining — placeholders until their steps
        composable("denials") {
            androidx.compose.material3.Text("Denials — coming in Step 9")
        }
        composable("cleanup") {
            androidx.compose.material3.Text("Cleanup — coming in Step 8")
        }
        composable("contexts") {
            androidx.compose.material3.Text("Contexts — coming in Step 10")
        }
        composable("validator") {
            androidx.compose.material3.Text("Validator — coming in Step 11")
        }
        composable("conflicts") {
            androidx.compose.material3.Text("Conflicts — coming in Step 12")
        }
        composable("explorer") {
            androidx.compose.material3.Text("Explorer — coming in Step 13")
        }
        composable("diff") {
            androidx.compose.material3.Text("Diff — coming in Step 14")
        }
        composable("compile") {
            androidx.compose.material3.Text("Compile — coming in Step 15")
        }
    }
}

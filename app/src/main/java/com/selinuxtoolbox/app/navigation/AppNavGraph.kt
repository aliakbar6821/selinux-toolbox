package com.selinuxtoolbox.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.selinuxtoolbox.feature.projects.PROJECTS_ROUTE
import com.selinuxtoolbox.feature.projects.projectsGraph

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val CLEANUP = "cleanup"
    const val DENIALS = "denials"
    const val CONTEXTS = "contexts"
    const val DIFF = "diff"
    const val COMPILE = "compile"
    const val EXPLORER = "explorer"
    const val PROJECTS = PROJECTS_ROUTE
    const val CONFLICTS = "conflicts"
    const val VALIDATOR = "validator"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppRoutes.PROJECTS   // start at projects until dashboard is built
    ) {
        // Feature: Projects (Step 5 — fully wired)
        projectsGraph(navController = navController)

        // Remaining features — placeholder composables until their steps are built
        composable(AppRoutes.DASHBOARD) {
            androidx.compose.material3.Text("Dashboard — coming in Step 6")
        }
        composable(AppRoutes.CLEANUP) {
            androidx.compose.material3.Text("Cleanup — coming in Step 8")
        }
        composable(AppRoutes.DENIALS) {
            androidx.compose.material3.Text("Denials — coming in Step 9")
        }
        composable(AppRoutes.CONTEXTS) {
            androidx.compose.material3.Text("Contexts — coming in Step 10")
        }
        composable(AppRoutes.VALIDATOR) {
            androidx.compose.material3.Text("Validator — coming in Step 11")
        }
        composable(AppRoutes.CONFLICTS) {
            androidx.compose.material3.Text("Conflicts — coming in Step 12")
        }
        composable(AppRoutes.EXPLORER) {
            androidx.compose.material3.Text("Explorer — coming in Step 13")
        }
        composable(AppRoutes.DIFF) {
            androidx.compose.material3.Text("Diff — coming in Step 14")
        }
        composable(AppRoutes.COMPILE) {
            androidx.compose.material3.Text("Compile — coming in Step 15")
        }
    }
}

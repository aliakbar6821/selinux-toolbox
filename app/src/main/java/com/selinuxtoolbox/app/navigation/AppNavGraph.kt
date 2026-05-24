package com.selinuxtoolbox.app.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val CLEANUP = "cleanup"
    const val DENIALS = "denials"
    const val CONTEXTS = "contexts"
    const val DIFF = "diff"
    const val COMPILE = "compile"
    const val EXPLORER = "explorer"
    const val PROJECTS = "projects"
    const val CONFLICTS = "conflicts"
    const val VALIDATOR = "validator"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DASHBOARD
    ) {
        composable(AppRoutes.DASHBOARD) {
            Text("SELinux Toolbox — skeleton build OK")
        }
    }
}

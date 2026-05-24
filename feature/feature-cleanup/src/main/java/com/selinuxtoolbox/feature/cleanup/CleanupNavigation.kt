package com.selinuxtoolbox.feature.cleanup

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val CLEANUP_ROUTE = "cleanup"

fun NavController.navigateToCleanup() = navigate(CLEANUP_ROUTE)

fun NavGraphBuilder.cleanupScreen() {
    composable(CLEANUP_ROUTE) {
        CleanupScreen()
    }
}

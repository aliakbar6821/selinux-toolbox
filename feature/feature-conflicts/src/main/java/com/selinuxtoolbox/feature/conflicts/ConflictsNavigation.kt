package com.selinuxtoolbox.feature.conflicts

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val CONFLICTS_ROUTE = "conflicts"

fun NavController.navigateToConflicts() = navigate(CONFLICTS_ROUTE)

fun NavGraphBuilder.conflictsScreen() {
    composable(CONFLICTS_ROUTE) {
        ConflictsScreen()
    }
}

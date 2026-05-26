package com.selinuxtoolbox.feature.explorer

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val EXPLORER_ROUTE = "explorer"

fun NavController.navigateToExplorer() = navigate(EXPLORER_ROUTE)

fun NavGraphBuilder.explorerScreen() {
    composable(EXPLORER_ROUTE) {
        ExplorerScreen()
    }
}

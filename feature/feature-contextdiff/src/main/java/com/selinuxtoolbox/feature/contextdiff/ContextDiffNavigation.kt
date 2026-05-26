package com.selinuxtoolbox.feature.contextdiff

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val CONTEXT_DIFF_ROUTE = "context_diff"

fun NavController.navigateToContextDiff() = navigate(CONTEXT_DIFF_ROUTE)

fun NavGraphBuilder.contextDiffScreen() {
    composable(CONTEXT_DIFF_ROUTE) {
        ContextDiffScreen()
    }
}

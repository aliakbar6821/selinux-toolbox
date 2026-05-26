package com.selinuxtoolbox.feature.diff

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val DIFF_ROUTE = "diff"

fun NavController.navigateToDiff() = navigate(DIFF_ROUTE)

fun NavGraphBuilder.diffScreen() {
    composable(DIFF_ROUTE) {
        DiffScreen()
    }
}

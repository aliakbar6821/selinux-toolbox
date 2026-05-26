package com.selinuxtoolbox.feature.contexts

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val CONTEXTS_ROUTE = "contexts"

fun NavController.navigateToContexts() = navigate(CONTEXTS_ROUTE)

fun NavGraphBuilder.contextsScreen() {
    composable(CONTEXTS_ROUTE) {
        ContextsScreen()
    }
}

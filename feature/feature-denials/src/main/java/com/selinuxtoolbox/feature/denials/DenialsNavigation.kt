package com.selinuxtoolbox.feature.denials

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val DENIALS_ROUTE = "denials"

fun NavController.navigateToDenials() = navigate(DENIALS_ROUTE)

fun NavGraphBuilder.denialsScreen() {
    composable(DENIALS_ROUTE) {
        DenialsScreen()
    }
}

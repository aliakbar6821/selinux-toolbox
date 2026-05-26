package com.selinuxtoolbox.feature.compile

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val COMPILE_ROUTE = "compile"

fun NavController.navigateToCompile() = navigate(COMPILE_ROUTE)

fun NavGraphBuilder.compileScreen() {
    composable(COMPILE_ROUTE) {
        CompileScreen()
    }
}

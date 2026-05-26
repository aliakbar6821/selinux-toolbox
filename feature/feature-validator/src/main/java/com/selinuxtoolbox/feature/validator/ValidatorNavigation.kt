package com.selinuxtoolbox.feature.validator

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val VALIDATOR_ROUTE = "validator"

fun NavController.navigateToValidator() = navigate(VALIDATOR_ROUTE)

fun NavGraphBuilder.validatorScreen() {
    composable(VALIDATOR_ROUTE) {
        ValidatorScreen()
    }
}

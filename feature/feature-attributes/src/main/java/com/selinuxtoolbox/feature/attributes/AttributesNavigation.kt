package com.selinuxtoolbox.feature.attributes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val ATTRIBUTES_ROUTE = "attributes"

fun NavController.navigateToAttributes() = navigate(ATTRIBUTES_ROUTE)

fun NavGraphBuilder.attributesScreen() {
    composable(ATTRIBUTES_ROUTE) {
        AttributesScreen()
    }
}

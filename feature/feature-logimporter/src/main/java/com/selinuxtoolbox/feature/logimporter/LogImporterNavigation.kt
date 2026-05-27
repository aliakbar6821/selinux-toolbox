package com.selinuxtoolbox.feature.logimporter

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val LOG_IMPORTER_ROUTE = "log_importer"

fun NavController.navigateToLogImporter() = navigate(LOG_IMPORTER_ROUTE)

fun NavGraphBuilder.logImporterScreen() {
    composable(LOG_IMPORTER_ROUTE) {
        LogImporterScreen()
    }
}

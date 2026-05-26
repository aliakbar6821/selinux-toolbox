package com.selinuxtoolbox.feature.offline

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val RC_SCAN_ROUTE = "rc_scan"

fun NavController.navigateToRcScan() = navigate(RC_SCAN_ROUTE)

fun NavGraphBuilder.rcScanScreen() {
    composable(RC_SCAN_ROUTE) {
        RcScanScreen()
    }
}

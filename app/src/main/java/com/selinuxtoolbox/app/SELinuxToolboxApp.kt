package com.selinuxtoolbox.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.selinuxtoolbox.app.navigation.AppNavGraph
import com.selinuxtoolbox.core.ui.theme.SELinuxToolboxTheme

@Composable
fun SELinuxToolboxApp() {
    SELinuxToolboxTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        // Removed ModalNavigationDrawer completely to avoid Dashboard UI conflicts
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            AppNavGraph(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

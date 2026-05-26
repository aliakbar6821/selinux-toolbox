package com.selinuxtoolbox.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.selinuxtoolbox.app.navigation.AppNavGraph
import com.selinuxtoolbox.core.ui.theme.SELinuxToolboxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SELinuxToolboxTheme {
                // Show storage permission dialog if not yet granted
                var showStorageDialog by remember {
                    mutableStateOf(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        !Environment.isExternalStorageManager()
                    )
                }

                if (showStorageDialog) {
                    AlertDialog(
                        onDismissRequest = { /* force user to decide */ },
                        title = { Text("Storage Permission Required") },
                        text  = {
                            Text(
                                "SELinux Toolbox needs full access to manage files on SD card " +
                                "(OEM/, AOSP/, work/ folders).\n\n" +
                                "Tap Grant to open Settings → Special App Access → " +
                                "All Files Access, then enable it for SELinux Toolbox."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showStorageDialog = false
                                requestManageStoragePermission()
                            }) {
                                Text("Grant")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStorageDialog = false }) {
                                Text("Later")
                            }
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check after user returns from Settings
        // (no action needed — Compose will recompose if state changes)
    }

    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback: open general storage settings
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }
}

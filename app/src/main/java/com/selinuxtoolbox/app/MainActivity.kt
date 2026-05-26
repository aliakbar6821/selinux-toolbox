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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

                // Reactive permission state — re-evaluated on every ON_RESUME
                var hasAllFilesAccess by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            Environment.isExternalStorageManager()
                        else
                            true
                    )
                }

                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ) {
                            hasAllFilesAccess = Environment.isExternalStorageManager()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                if (!hasAllFilesAccess) {
                    AlertDialog(
                        onDismissRequest = { /* force user to decide */ },
                        title = { Text("Storage Permission Required") },
                        text = {
                            Text(
                                "SELinux Toolbox needs All Files Access to read and write " +
                                "OEM/, AOSP/, and work/ folders on SD card.\n\n" +
                                "Tap Grant → Settings → Special App Access → " +
                                "All Files Access → enable SELinux Toolbox."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { requestManageStoragePermission() }) {
                                Text("Grant")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { hasAllFilesAccess = true }) {
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

    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }
}

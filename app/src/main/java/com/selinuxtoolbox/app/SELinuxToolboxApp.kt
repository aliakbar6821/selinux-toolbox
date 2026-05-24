package com.selinuxtoolbox.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.selinuxtoolbox.core.data.binary.BinaryManager
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SELinuxToolboxApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var binaryManager: BinaryManager

    // App-level scope — survives for the lifetime of the process.
    // SupervisorJob ensures one failure does not cancel other coroutines.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        // Configure libsu BEFORE super.onCreate() so it is ready
        // before any component tries to use root.
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30)
        )

        super.onCreate()

        // Extract secilc + sepolicy-analyze from assets to private dir.
        // Runs on IO dispatcher — does not block the main thread.
        // Failures are non-fatal at startup: features will show
        // "Binary not ready" state instead of crashing.
        appScope.launch(Dispatchers.IO) {
            binaryManager.initialize()
                .onFailure { e ->
                    // Log only — do not crash. UI will surface the error
                    // when user tries to use compile features.
                    if (BuildConfig.DEBUG) {
                        android.util.Log.e(
                            "SELinuxToolbox",
                            "BinaryManager init failed: ${e.message}"
                        )
                    }
                }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

package com.selinuxtoolbox.core.data.root

import com.selinuxtoolbox.core.model.RootStatus
import com.selinuxtoolbox.core.model.SelinuxMode
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootShell @Inject constructor() {

    // Check root availability and verify SELinux file access
    suspend fun checkRoot(): RootStatus = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (!shell.isRoot) return@withContext RootStatus.NotAvailable

            val result = Shell.cmd("cat /sys/fs/selinux/enforce 2>/dev/null").exec()
            if (result.isSuccess) {
                RootStatus.Available
            } else {
                RootStatus.Error("Root granted but cannot access SELinux files")
            }
        } catch (e: Exception) {
            RootStatus.Error(e.message ?: "Unknown root error")
        }
    }

    // Execute a single command and return stdout lines
    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(command).exec()
            ShellResult(
                success = result.isSuccess,
                stdout = result.out,
                stderr = result.err
            )
        } catch (e: Exception) {
            ShellResult(
                success = false,
                stdout = emptyList(),
                stderr = listOf(e.message ?: "Unknown error")
            )
        }
    }

    // Read a file via root — returns null if file doesn't exist or can't be read
    suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val result = Shell.cmd("cat '$path' 2>/dev/null").exec()
        if (result.isSuccess && result.out.isNotEmpty()) {
            result.out.joinToString("\n")
        } else null
    }

    // Check if a file exists via root
    suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("test -f '$path' && echo yes").exec().out.firstOrNull() == "yes"
    }

    // Check if a directory exists via root
    suspend fun dirExists(path: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("test -d '$path' && echo yes").exec().out.firstOrNull() == "yes"
    }

    // List files in a directory via root
    suspend fun listFiles(path: String): List<String> = withContext(Dispatchers.IO) {
        val result = Shell.cmd("ls '$path' 2>/dev/null").exec()
        if (result.isSuccess) result.out else emptyList()
    }

    // List files with full paths recursively
    suspend fun listFilesRecursive(path: String, extension: String = ""): List<String> =
        withContext(Dispatchers.IO) {
            val filter = if (extension.isNotEmpty()) "-name '*.$extension'" else ""
            val result = Shell.cmd("find '$path' -type f $filter 2>/dev/null").exec()
            if (result.isSuccess) result.out else emptyList()
        }

    // Get current SELinux mode
    suspend fun getSelinuxMode(): SelinuxMode = withContext(Dispatchers.IO) {
        val result = Shell.cmd("getenforce 2>/dev/null").exec()
        when (result.out.firstOrNull()?.trim()) {
            "Enforcing" -> SelinuxMode.ENFORCING
            "Permissive" -> SelinuxMode.PERMISSIVE
            "Disabled" -> SelinuxMode.DISABLED
            else -> SelinuxMode.UNKNOWN
        }
    }

    // Set SELinux mode (requires root)
    suspend fun setSelinuxMode(enforcing: Boolean): Boolean = withContext(Dispatchers.IO) {
        val value = if (enforcing) "1" else "0"
        Shell.cmd("setenforce $value 2>/dev/null").exec().isSuccess
    }

    // Get a system property
    suspend fun getProperty(prop: String): String? = withContext(Dispatchers.IO) {
        val result = Shell.cmd("getprop '$prop' 2>/dev/null").exec()
        result.out.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    // Stream dmesg AVC denials in real time
    fun streamAvcDenials(): Flow<String> = callbackFlow {
        val job = Shell.cmd("dmesg -w 2>/dev/null").to(
            object : com.topjohnwu.superuser.Shell.ResultCallback {
                override fun onResult(out: Shell.Result) {}
            }
        )

        // Use a simpler approach — poll dmesg for new avc lines
        // Full streaming requires a persistent process which we handle separately
        val result = Shell.cmd("dmesg 2>/dev/null | grep 'avc: '").exec()
        result.out.forEach { line ->
            trySend(line)
        }
        awaitClose { }
    }

    // Get running processes with SELinux contexts
    suspend fun getProcessContexts(): List<String> = withContext(Dispatchers.IO) {
        val result = Shell.cmd("ps -eZ 2>/dev/null || ps -AZ 2>/dev/null").exec()
        if (result.isSuccess) result.out else emptyList()
    }

    // List files with SELinux context in a directory
    suspend fun listFilesWithContext(path: String): List<String> = withContext(Dispatchers.IO) {
        val result = Shell.cmd("ls -laZ '$path' 2>/dev/null").exec()
        if (result.isSuccess) result.out else emptyList()
    }

    // Get vendor API level
    suspend fun getVendorApiLevel(): Int = withContext(Dispatchers.IO) {
        val prop = getProperty("ro.vndk.version") ?: return@withContext 0
        prop.toIntOrNull() ?: 0
    }

    // Get Android version
    suspend fun getAndroidVersion(): String = withContext(Dispatchers.IO) {
        getProperty("ro.build.version.release") ?: "Unknown"
    }

    // Get device model
    suspend fun getDeviceModel(): String = withContext(Dispatchers.IO) {
        val model = getProperty("ro.product.model") ?: ""
        val device = getProperty("ro.product.device") ?: ""
        "$model ($device)".trim()
    }
}

data class ShellResult(
    val success: Boolean,
    val stdout: List<String>,
    val stderr: List<String>
) {
    val output: String get() = stdout.joinToString("\n")
    val error: String get() = stderr.joinToString("\n")
}

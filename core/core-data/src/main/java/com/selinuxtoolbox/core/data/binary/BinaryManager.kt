package com.selinuxtoolbox.core.data.binary

import android.content.Context
import android.util.Log
import com.selinuxtoolbox.core.data.root.RootShell
import com.selinuxtoolbox.core.data.root.ShellResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val BINARY_SECILC          = "secilc"
private const val BINARY_SEPOLICY_ANALYZE = "sepolicy-analyze"
private const val ASSETS_BIN_DIR         = "bin"
private const val TAG = "BinaryManager"

data class BinaryInfo(
    val name: String,
    val path: String,
    val isReady: Boolean,
    val version: String?,
    val error: String? = null
)

sealed class BinaryResult {
    data class Success(
        val stdout: List<String>,
        val stderr: List<String>
    ) : BinaryResult()

    data class Failure(
        val exitCode: Int,
        val stderr: List<String>,
        val stdout: List<String>
    ) : BinaryResult()

    data class BinaryNotReady(val binaryName: String, val error: String? = null) : BinaryResult()
}

@Singleton
class BinaryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootShell: RootShell
) {

    private val binDir: File by lazy {
        File(context.filesDir, "bin").also { it.mkdirs() }
    }

    val secilcFile: File
        get() = File(binDir, BINARY_SECILC)

    val sepolicyAnalyzeFile: File
        get() = File(binDir, BINARY_SEPOLICY_ANALYZE)

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing binaries in ${binDir.absolutePath}")
            val secilcOk = extractBinary(BINARY_SECILC)
            val sepolicyOk = extractBinary(BINARY_SEPOLICY_ANALYZE)
            
            if (!secilcOk || !sepolicyOk) {
                val error = "Binary extraction failed: secilc=$secilcOk, sepolicy=$sepolicyOk"
                Log.e(TAG, error)
                return@withContext Result.failure(Exception(error))
            }
            
            // Verify the binaries actually work
            if (!verifyBinary(secilcFile, BINARY_SECILC)) {
                return@withContext Result.failure(Exception("$BINARY_SECILC verification failed"))
            }
            if (!verifyBinary(sepolicyAnalyzeFile, BINARY_SEPOLICY_ANALYZE)) {
                return@withContext Result.failure(Exception("$BINARY_SEPOLICY_ANALYZE verification failed"))
            }
            
            Log.d(TAG, "Binaries initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "BinaryManager init failed", e)
            Result.failure(e)
        }
    }

    private suspend fun extractBinary(name: String): Boolean = withContext(Dispatchers.IO) {
        val outFile = File(binDir, name)
        val assetPath = "$ASSETS_BIN_DIR/$name"

        try {
            // Check if we already have a valid binary
            if (outFile.exists() && outFile.length > 0 && outFile.canExecute()) {
                Log.d(TAG, "$name already exists and is executable, skipping extraction")
                return@withContext true
            }

            // Open asset and get size
            val assetSize = context.assets.openFd(assetPath).use { it.length() }
            Log.d(TAG, "Extracting $name from assets (size: $assetSize bytes)")

            // Delete any existing file
            if (outFile.exists()) {
                outFile.delete()
            }

            // Copy from assets
            context.assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Check size
            if (!outFile.exists() || outFile.length != assetSize) {
                val size = if (outFile.exists()) outFile.length else 0
                Log.e(TAG, "$name extraction failed: expected $assetSize bytes, got $size bytes")
                return@withContext false
            }

            // Make executable
            if (!outFile.setExecutable(true, false)) {
                Log.e(TAG, "Failed to set executable permission on $name")
                return@withContext false
            }

            Log.d(TAG, "$name extracted successfully (${outFile.length} bytes)")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract $name", e)
            return@withContext false
        }
    }

    private suspend fun verifyBinary(file: File, name: String): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canExecute()) {
            Log.e(TAG, "$name not found or not executable")
            return@withContext false
        }
        try {
            val result = rootShell.execute("${file.absolutePath} --version 2>&1 || true")
            val output = (result.stdout + result.stderr).joinToString("\n")
            if (output.contains("secilc") || output.contains("sepolicy-analyze") || output.trim().isNotEmpty()) {
                Log.d(TAG, "$name verification passed")
                return@withContext true
            } else {
                Log.e(TAG, "$name verification failed: output was '$output'")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "$name verification threw exception", e)
            return@withContext false
        }
    }

    // -------------------------------------------------------------------------
    // Status checks
    // -------------------------------------------------------------------------

    suspend fun getBinaryInfo(): List<BinaryInfo> = withContext(Dispatchers.IO) {
        listOf(
            buildInfo(BINARY_SECILC, secilcFile),
            buildInfo(BINARY_SEPOLICY_ANALYZE, sepolicyAnalyzeFile)
        )
    }

    private suspend fun buildInfo(name: String, file: File): BinaryInfo {
        val ready = file.exists() && file.canExecute() && verifyBinary(file, name)
        val version = if (ready) queryVersion(file) else null
        return BinaryInfo(
            name      = name,
            path      = file.absolutePath,
            isReady   = ready,
            version   = version,
            error     = if (!ready && file.exists()) "Binary exists but not executable or verification failed" else null
        )
    }

    private suspend fun queryVersion(binary: File): String? {
        return try {
            val result = rootShell.execute("${binary.absolutePath} --version 2>&1 | head -1")
            result.stdout.firstOrNull()?.trim()
                ?: result.stderr.firstOrNull()?.trim()
                ?: "Unknown"
        } catch (e: Exception) {
            null
        }
    }

    fun isSecilcReady(): Boolean = secilcFile.exists() && secilcFile.canExecute() && secilcFile.length > 0
    fun isSepolicyAnalyzeReady(): Boolean = sepolicyAnalyzeFile.exists() && sepolicyAnalyzeFile.canExecute() && sepolicyAnalyzeFile.length > 0

    // -------------------------------------------------------------------------
    // secilc operations
    // -------------------------------------------------------------------------

    suspend fun secilcDryRun(
        cilFiles: List<String>,
        policyVersion: Int = 31
    ): BinaryResult = withContext(Dispatchers.IO) {
        if (!isSecilcReady()) return@withContext BinaryResult.BinaryNotReady(BINARY_SECILC, "secilc binary not ready")
        if (cilFiles.isEmpty()) return@withContext BinaryResult.Failure(1, listOf("No CIL files provided"), emptyList())

        val cmd = buildSecilcCommand(cilFiles, "/dev/null", "/dev/null", policyVersion)
        executeAndReturn(cmd)
    }

    suspend fun secilcCompile(
        cilFiles: List<String>,
        outputPath: String,
        policyVersion: Int = 31
    ): BinaryResult = withContext(Dispatchers.IO) {
        if (!isSecilcReady()) return@withContext BinaryResult.BinaryNotReady(BINARY_SECILC, "secilc binary not ready")

        val outFile = File(outputPath)
        outFile.parentFile?.mkdirs()

        val cmd = buildSecilcCommand(cilFiles, outputPath, "/dev/null", policyVersion)
        executeAndReturn(cmd)
    }

    private fun buildSecilcCommand(
        cilFiles: List<String>,
        outputPath: String,
        fcPath: String,
        policyVersion: Int
    ): String {
        val binary = secilcFile.absolutePath
        val fileArgs = cilFiles.joinToString(" ") { "'$it'" }
        return "$binary -o '$outputPath' -f '$fcPath' -c $policyVersion $fileArgs 2>&1"
    }

    // -------------------------------------------------------------------------
    // sepolicy-analyze operations
    // -------------------------------------------------------------------------

    suspend fun listTypes(compiledPolicyPath: String): BinaryResult = withContext(Dispatchers.IO) {
        if (!isSepolicyAnalyzeReady()) {
            return@withContext BinaryResult.BinaryNotReady(BINARY_SEPOLICY_ANALYZE)
        }
        val cmd = "${sepolicyAnalyzeFile.absolutePath} '$compiledPolicyPath' print types 2>&1"
        executeAndReturn(cmd)
    }

    suspend fun findAllowRules(compiledPolicyPath: String, typeName: String): BinaryResult = withContext(Dispatchers.IO) {
        if (!isSepolicyAnalyzeReady()) {
            return@withContext BinaryResult.BinaryNotReady(BINARY_SEPOLICY_ANALYZE)
        }
        val cmd = "${sepolicyAnalyzeFile.absolutePath} '$compiledPolicyPath' allow -s '$typeName' 2>&1"
        executeAndReturn(cmd)
    }

    suspend fun checkNeverallow(compiledPolicyPath: String): BinaryResult = withContext(Dispatchers.IO) {
        if (!isSepolicyAnalyzeReady()) {
            return@withContext BinaryResult.BinaryNotReady(BINARY_SEPOLICY_ANALYZE)
        }
        val cmd = "${sepolicyAnalyzeFile.absolutePath} '$compiledPolicyPath' neverallow 2>&1"
        executeAndReturn(cmd)
    }

    private suspend fun executeAndReturn(command: String): BinaryResult {
        val result: ShellResult = rootShell.execute(command)
        return if (result.success) {
            BinaryResult.Success(result.stdout, result.stderr)
        } else {
            BinaryResult.Failure(1, result.stderr, result.stdout)
        }
    }

    suspend fun clean() = withContext(Dispatchers.IO) {
        binDir.listFiles()?.forEach { it.delete() }
    }
}

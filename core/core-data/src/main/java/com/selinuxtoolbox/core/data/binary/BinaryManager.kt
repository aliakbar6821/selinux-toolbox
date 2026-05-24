package com.selinuxtoolbox.core.data.binary

import android.content.Context
import com.selinuxtoolbox.core.data.root.RootShell
import com.selinuxtoolbox.core.data.root.ShellResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Names must match exactly what is placed in app/src/main/assets/bin/
private const val BINARY_SECILC          = "secilc"
private const val BINARY_SEPOLICY_ANALYZE = "sepolicy-analyze"
private const val ASSETS_BIN_DIR         = "bin"

data class BinaryInfo(
    val name: String,
    val path: String,       // absolute path in app private dir
    val isReady: Boolean,   // extracted + executable
    val version: String?    // from --version if supported
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

    data class BinaryNotReady(val binaryName: String) : BinaryResult()
}

@Singleton
class BinaryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootShell: RootShell
) {

    // Private dir that is always accessible without root or storage perms.
    // Path: /data/data/<packageName>/files/bin/
    private val binDir: File by lazy {
        File(context.filesDir, "bin").also { it.mkdirs() }
    }

    val secilcFile: File
        get() = File(binDir, BINARY_SECILC)

    val sepolicyAnalyzeFile: File
        get() = File(binDir, BINARY_SEPOLICY_ANALYZE)

    // -------------------------------------------------------------------------
    // Initialization — call once at app start or before first use
    // -------------------------------------------------------------------------

    // Extract all binaries from assets and make them executable.
    // Safe to call multiple times — skips if already extracted and unchanged.
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            extractBinary(BINARY_SECILC)
            extractBinary(BINARY_SEPOLICY_ANALYZE)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("BinaryManager init failed: ${e.message}", e))
        }
    }

    // Extract a single binary from assets to private bin dir.
    // Uses size comparison to detect if re-extraction is needed.
    private fun extractBinary(name: String) {
        val outFile = File(binDir, name)
        val assetPath = "$ASSETS_BIN_DIR/$name"

        val assetSize = try {
            context.assets.openFd(assetPath).use { it.length }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Binary '$name' not found in assets/$ASSETS_BIN_DIR/. " +
                "Place ARM64 static binary at app/src/main/assets/$ASSETS_BIN_DIR/$name"
            )
        }

        // Re-extract only if missing or size differs (avoids unnecessary IO)
        if (outFile.exists() && outFile.length() == assetSize) {
            // Already extracted — ensure it is still executable
            if (!outFile.canExecute()) outFile.setExecutable(true, false)
            return
        }

        context.assets.open(assetPath).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        outFile.setExecutable(true, false)
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
        val ready = file.exists() && file.canExecute()
        val version = if (ready) queryVersion(file) else null
        return BinaryInfo(
            name      = name,
            path      = file.absolutePath,
            isReady   = ready,
            version   = version
        )
    }

    // Try to get version string. secilc does not have --version but prints
    // usage to stderr on no args — we capture that as the version signal.
    private suspend fun queryVersion(binary: File): String? {
        return try {
            val result = rootShell.execute("${binary.absolutePath} --version 2>&1 | head -1")
            result.stdout.firstOrNull()?.trim()
                ?: result.stderr.firstOrNull()?.trim()
        } catch (e: Exception) {
            null
        }
    }

    fun isSecilcReady(): Boolean = secilcFile.exists() && secilcFile.canExecute()
    fun isSepolicyAnalyzeReady(): Boolean =
        sepolicyAnalyzeFile.exists() && sepolicyAnalyzeFile.canExecute()

    // -------------------------------------------------------------------------
    // secilc — compile CIL files into binary policy
    // -------------------------------------------------------------------------

    // Dry-run validation: compile to /dev/null to check for errors.
    // This is ALWAYS run before actual compilation (constraint from brief).
    //
    // cilFiles: ordered list of absolute paths to .cil files
    // policyVersion: Android policy version (usually matches API level, e.g. 31)
    //
    // Returns BinaryResult.Success if secilc exits 0, Failure with errors otherwise.
    suspend fun secilcDryRun(
        cilFiles: List<String>,
        policyVersion: Int = 31
    ): BinaryResult = withContext(Dispatchers.IO) {
        if (!isSecilcReady()) return@withContext BinaryResult.BinaryNotReady(BINARY_SECILC)
        if (cilFiles.isEmpty()) return@withContext BinaryResult.Failure(
            exitCode = 1,
            stderr   = listOf("No CIL files provided"),
            stdout   = emptyList()
        )

        val cmd = buildSecilcCommand(
            cilFiles      = cilFiles,
            outputPath    = "/dev/null",
            fcPath        = "/dev/null",
            policyVersion = policyVersion
        )
        executeAndReturn(cmd)
    }

    // Actual compilation — only call after dry-run succeeds.
    // outputPath: full path on SD card where precompiled_sepolicy will be written
    suspend fun secilcCompile(
        cilFiles: List<String>,
        outputPath: String,
        policyVersion: Int = 31
    ): BinaryResult = withContext(Dispatchers.IO) {
        if (!isSecilcReady()) return@withContext BinaryResult.BinaryNotReady(BINARY_SECILC)

        // Ensure output directory exists
        val outFile = File(outputPath)
        outFile.parentFile?.mkdirs()

        val cmd = buildSecilcCommand(
            cilFiles      = cilFiles,
            outputPath    = outputPath,
            fcPath        = "/dev/null",
            policyVersion = policyVersion
        )
        executeAndReturn(cmd)
    }

    // Build the secilc command string.
    // Correct argument order per AOSP:
    //   secilc -o <output> -f <file_contexts> -c <version> [cil files...]
    private fun buildSecilcCommand(
        cilFiles: List<String>,
        outputPath: String,
        fcPath: String,
        policyVersion: Int
    ): String {
        val binary    = secilcFile.absolutePath
        val fileArgs  = cilFiles.joinToString(" ") { "'$it'" }
        return "$binary -o '$outputPath' -f '$fcPath' -c $policyVersion $fileArgs 2>&1"
    }

    // -------------------------------------------------------------------------
    // sepolicy-analyze — query the compiled binary policy
    // -------------------------------------------------------------------------

    // List all types in a compiled binary policy file
    suspend fun listTypes(compiledPolicyPath: String): BinaryResult =
        withContext(Dispatchers.IO) {
            if (!isSepolicyAnalyzeReady()) {
                return@withContext BinaryResult.BinaryNotReady(BINARY_SEPOLICY_ANALYZE)
            }
            val cmd = "${sepolicyAnalyzeFile.absolutePath} '$compiledPolicyPath' " +
                      "print types 2>&1"
            executeAndReturn(cmd)
        }

    // Find all allow rules for a type
    suspend fun findAllowRules(
        compiledPolicyPath: String,
        typeName: String
    ): BinaryResult = withContext(Dispatchers.IO) {
        if (!isSepolicyAnalyzeReady()) {
            return@withContext BinaryResult.BinaryNotReady(BINARY_SEPOLICY_ANALYZE)
        }
        val cmd = "${sepolicyAnalyzeFile.absolutePath} '$compiledPolicyPath' " +
                  "allow -s '$typeName' 2>&1"
        executeAndReturn(cmd)
    }

    // Check for neverallow violations in a compiled policy
    suspend fun checkNeverallow(compiledPolicyPath: String): BinaryResult =
        withContext(Dispatchers.IO) {
            if (!isSepolicyAnalyzeReady()) {
                return@withContext BinaryResult.BinaryNotReady(BINARY_SEPOLICY_ANALYZE)
            }
            val cmd = "${sepolicyAnalyzeFile.absolutePath} '$compiledPolicyPath' " +
                      "neverallow 2>&1"
            executeAndReturn(cmd)
        }

    // -------------------------------------------------------------------------
    // Generic execution helper
    // -------------------------------------------------------------------------

    // Execute a command via root shell and return structured BinaryResult.
    // We always run bundled binaries through the root shell because:
    //   1. The binary needs to read policy files on read-only EROFS partitions
    //   2. Consistent execution environment
    private suspend fun executeAndReturn(command: String): BinaryResult {
        val result: ShellResult = rootShell.execute(command)
        return if (result.success) {
            BinaryResult.Success(
                stdout = result.stdout,
                stderr = result.stderr
            )
        } else {
            BinaryResult.Failure(
                exitCode = 1, // libsu does not expose exit code directly
                stderr   = result.stderr,
                stdout   = result.stdout
            )
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    // Remove extracted binaries (e.g. for re-extraction after app update)
    suspend fun clean() = withContext(Dispatchers.IO) {
        binDir.listFiles()?.forEach { it.delete() }
    }
}

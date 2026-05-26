package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.binary.BinaryManager
import com.selinuxtoolbox.core.data.binary.BinaryResult
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.model.ActiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class CompileResult {
    data class Success(
        val outputPath: String,
        val sizeBytes: Long,
        val filesCompiled: Int
    ) : CompileResult()

    data class DryRunFailed(
        val errors: List<SecilcError>,
        val preChecks: List<PreCheckResult>
    ) : CompileResult()

    data class CompileFailed(val stderr: List<String>) : CompileResult()
    data class SetupError(val reason: String) : CompileResult()
}

@Singleton
class CompilePolicyUseCase @Inject constructor(
    private val binaryManager: BinaryManager,
    private val pathResolver: PathResolver
) {
    suspend operator fun invoke(
        aospPath: String,
        workPath: String,
        outputPath: String,
        mode: ActiveMode,
        policyVersion: Int = 31
    ): CompileResult = withContext(Dispatchers.IO) {

        if (!binaryManager.isSecilcReady()) {
            return@withContext CompileResult.SetupError(
                "secilc binary not ready. Open Settings and initialize binaries first."
            )
        }

        // Build CIL load order — identical to ValidateCilUseCase
        val mappingVersion = pathResolver.readMappingVersion(aospPath, mode)
        val baseCilFiles   = pathResolver.buildCilLoadOrder(
            aospPath       = aospPath,
            mappingVersion = mappingVersion,
            mode           = mode
        )

        val outputsDir   = File(workPath, "outputs")
        val generatedCil = if (outputsDir.exists()) {
            outputsDir.walkTopDown()
                .filter { it.isFile && it.extension == "cil" }
                .sortedBy { it.absolutePath }
                .toList()
        } else emptyList()

        val attrFiles  = generatedCil.filter { it.name.contains("attribute") }
        val otherFiles = generatedCil.filter { !it.name.contains("attribute") }

        val vendorIdx = baseCilFiles.indexOfFirst { it.name == "vendor_sepolicy.cil" }
        val allFiles  = if (vendorIdx >= 0) {
            baseCilFiles.toMutableList().also {
                it.addAll(vendorIdx, attrFiles)
                it.addAll(vendorIdx + attrFiles.size + 1, otherFiles)
            }
        } else {
            baseCilFiles + attrFiles + otherFiles
        }

        if (allFiles.isEmpty()) {
            return@withContext CompileResult.SetupError(
                "No CIL files found. Check aospPath is set correctly:\n$aospPath"
            )
        }

        val cilPaths  = allFiles.map { it.absolutePath }
        val preChecks = runPreChecks(allFiles)

        // ── Step 1: Dry-run ───────────────────────────────────────────────────
        when (val dry = binaryManager.secilcDryRun(cilPaths, policyVersion)) {
            is BinaryResult.BinaryNotReady ->
                return@withContext CompileResult.SetupError("secilc not ready: ${dry.binaryName}")
            is BinaryResult.Failure ->
                return@withContext CompileResult.DryRunFailed(
                    errors    = parseErrors(dry.stderr.joinToString("\n")),
                    preChecks = preChecks
                )
            is BinaryResult.Success -> { /* dry-run passed — proceed */ }
        }

        // ── Step 2: Real compile ──────────────────────────────────────────────
        File(outputPath).parentFile?.mkdirs()

        when (val compile = binaryManager.secilcCompile(cilPaths, outputPath, policyVersion)) {
            is BinaryResult.Success -> CompileResult.Success(
                outputPath    = outputPath,
                sizeBytes     = File(outputPath).takeIf { it.exists() }?.length() ?: 0L,
                filesCompiled = allFiles.size
            )
            is BinaryResult.Failure -> CompileResult.CompileFailed(compile.stderr)
            is BinaryResult.BinaryNotReady -> CompileResult.SetupError(
                "secilc not ready: ${compile.binaryName}"
            )
        }
    }

    private fun runPreChecks(files: List<File>): List<PreCheckResult> =
        files.filter { it.exists() && it.extension == "cil" }.map { file ->
            val issues  = mutableListOf<String>()
            val content = try { file.readText() } catch (e: Exception) {
                return@map PreCheckResult(file.name, false, listOf("Cannot read: ${e.message}"))
            }
            var depth = 0
            content.forEach { c -> when (c) { '(' -> depth++; ')' -> depth-- } }
            if (depth != 0) issues.add("Unbalanced parentheses (depth=$depth)")
            PreCheckResult(file.name, issues.isEmpty(), issues)
        }

    private fun parseErrors(stderr: String): List<SecilcError> {
        val pattern = Regex("""^(.*?):(\d+):\s*(.+)$""", RegexOption.MULTILINE)
        return stderr.lines().filter { it.isNotBlank() }.map { line ->
            val m = pattern.find(line)
            if (m != null) {
                val (fp, ln, msg) = m.destructured
                SecilcError(File(fp).name, ln.toIntOrNull(), msg.trim(), line)
            } else SecilcError("", null, line.trim(), line)
        }
    }
}

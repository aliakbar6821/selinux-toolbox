package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.binary.BinaryManager
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.model.ActiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PreCheckResult(
    val fileName: String,
    val passed: Boolean,
    val issues: List<String>
)

sealed class CilValidationResult {
    data class Pass(
        val filesChecked: Int,
        val preChecks: List<PreCheckResult>
    ) : CilValidationResult()

    data class Fail(
        val exitCode: Int,
        val errors: List<SecilcError>,
        val preChecks: List<PreCheckResult>
    ) : CilValidationResult()

    data class SetupError(val reason: String) : CilValidationResult()
}

data class SecilcError(
    val file: String,
    val line: Int?,
    val message: String,
    val rawLine: String
)

@Singleton
class ValidateCilUseCase @Inject constructor(
    private val binaryManager: BinaryManager,
    private val pathResolver: PathResolver
) {
    suspend operator fun invoke(
        aospPath: String,
        workPath: String,
        mode: ActiveMode
    ): CilValidationResult = withContext(Dispatchers.IO) {

        // 1. Check secilc is ready
        if (!binaryManager.isSecilcReady()) {
            return@withContext CilValidationResult.SetupError(
                "secilc binary not ready. Open Settings and initialize binaries first."
            )
        }

        // 2. Build load order
        val mappingVersion = pathResolver.readMappingVersion(aospPath, mode)
        val baseCilFiles   = pathResolver.buildCilLoadOrder(
            aospPath       = aospPath,
            mappingVersion = mappingVersion,
            mode           = mode
        )

        // 3. Collect generated output CIL files from work/outputs/
        val outputsDir   = File(workPath, "outputs")
        val generatedCil = if (outputsDir.exists()) {
            outputsDir.walkTopDown()
                .filter { it.isFile && it.extension == "cil" }
                .sortedBy { it.absolutePath }
                .toList()
        } else emptyList()

        val attrFiles  = generatedCil.filter { it.name.contains("attribute") }
        val otherFiles = generatedCil.filter { !it.name.contains("attribute") }

        // Insert attr files just before vendor_sepolicy.cil
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
            return@withContext CilValidationResult.SetupError(
                "No CIL files found. Check aospPath is set correctly:\n$aospPath"
            )
        }

        // 4. Pre-checks
        val preChecks = runPreChecks(allFiles)

        // 5. Use BinaryManager.secilcDryRun — it runs secilc -o /dev/null internally
        val dryRunResult = binaryManager.secilcDryRun(
            cilFiles = allFiles.map { it.absolutePath }
        )

        if (dryRunResult.exitCode == 0) {
            CilValidationResult.Pass(
                filesChecked = allFiles.size,
                preChecks    = preChecks
            )
        } else {
            CilValidationResult.Fail(
                exitCode  = dryRunResult.exitCode,
                errors    = parseSecilcErrors(dryRunResult.stderr),
                preChecks = preChecks
            )
        }
    }

    private fun runPreChecks(files: List<File>): List<PreCheckResult> {
        return files.filter { it.exists() && it.extension == "cil" }.map { file ->
            val issues  = mutableListOf<String>()
            val content = try { file.readText() } catch (e: Exception) {
                return@map PreCheckResult(file.name, false, listOf("Cannot read: ${e.message}"))
            }

            // Parenthesis balance
            var depth = 0
            content.forEach { c -> when (c) { '(' -> depth++; ')' -> depth-- } }
            if (depth != 0) issues.add("Unbalanced parentheses (depth=$depth)")

            // Duplicate type declarations
            val dupes = Regex("""\(type\s+(\w+)\)""")
                .findAll(content).map { it.groupValues[1] }.toList()
                .groupBy { it }.filter { it.value.size > 1 }.keys
            if (dupes.isNotEmpty())
                issues.add("Duplicate types: ${dupes.take(5).joinToString(", ")}")

            PreCheckResult(file.name, issues.isEmpty(), issues)
        }
    }

    private fun parseSecilcErrors(stderr: String): List<SecilcError> {
        val pattern = Regex("""^(.*?):(\d+):\s*(.+)$""", RegexOption.MULTILINE)
        return stderr.lines().filter { it.isNotBlank() }.map { line ->
            val m = pattern.find(line)
            if (m != null) {
                val (filePath, lineNum, msg) = m.destructured
                SecilcError(File(filePath).name, lineNum.toIntOrNull(), msg.trim(), line)
            } else {
                SecilcError("", null, line.trim(), line)
            }
        }
    }
}

package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.binary.BinaryManager
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.model.ActiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Result of one pre-validation check (before secilc runs)
data class PreCheckResult(
    val fileName: String,
    val passed: Boolean,
    val issues: List<String>   // empty if passed
)

// Full validation result
sealed class CilValidationResult {
    data class Pass(
        val filesChecked: Int,
        val preChecks: List<PreCheckResult>
    ) : CilValidationResult()

    data class Fail(
        val exitCode: Int,
        val errors: List<SecilcError>,   // parsed from stderr
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

        // 1. Get secilc binary
        val secilc = binaryManager.getSecilcPath()
            ?: return@withContext CilValidationResult.SetupError(
                "secilc binary not found. Check app assets."
            )

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

        // Generated attribute files must load before vendor_sepolicy
        val attrFiles  = generatedCil.filter { it.name.contains("attribute") }
        val otherFiles = generatedCil.filter { !it.name.contains("attribute") }

        // Insert attr files just before vendor_sepolicy.cil in the load order
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
                "No CIL files found. Check that aospPath is set correctly:\n$aospPath"
            )
        }

        // 4. Pre-checks before running secilc
        val preChecks = runPreChecks(allFiles)

        // 5. Run secilc dry-run: -o /dev/null means no output binary written
        val cmd = mutableListOf(
            secilc,
            "-o", "/dev/null",
            "-f", "/dev/null",
            "-c", "31"
        ) + allFiles.map { it.absolutePath }

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(false)
            .start()

        val stderr   = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            CilValidationResult.Pass(
                filesChecked = allFiles.size,
                preChecks    = preChecks
            )
        } else {
            CilValidationResult.Fail(
                exitCode  = exitCode,
                errors    = parseSecilcErrors(stderr),
                preChecks = preChecks
            )
        }
    }

    // Pre-checks run before secilc — catch common issues fast
    private fun runPreChecks(files: List<File>): List<PreCheckResult> {
        val results = mutableListOf<PreCheckResult>()

        for (file in files.filter { it.exists() && it.extension == "cil" }) {
            val issues  = mutableListOf<String>()
            val content = try { file.readText() } catch (e: Exception) {
                results.add(PreCheckResult(file.name, false, listOf("Cannot read file: ${e.message}")))
                continue
            }

            // Check parenthesis balance
            var depth = 0
            content.forEach { c ->
                when (c) {
                    '(' -> depth++
                    ')' -> depth--
                }
            }
            if (depth != 0) issues.add("Unbalanced parentheses (net depth=$depth)")

            // Check for duplicate type declarations
            val typeDecls = Regex("""\(type\s+(\w+)\)""")
                .findAll(content)
                .map { it.groupValues[1] }
                .toList()
            val dupes = typeDecls.groupBy { it }
                .filter { it.value.size > 1 }
                .keys
            if (dupes.isNotEmpty()) {
                issues.add("Duplicate type declarations: ${dupes.take(5).joinToString(", ")}")
            }

            results.add(PreCheckResult(file.name, issues.isEmpty(), issues))
        }

        return results
    }

    // Parse secilc stderr into structured errors
    // Typical format: /path/to/file.cil:42: error: message
    private fun parseSecilcErrors(stderr: String): List<SecilcError> {
        val pattern = Regex("""^(.*?):(\d+):\s*(.+)$""", RegexOption.MULTILINE)
        return stderr.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                val match = pattern.find(line)
                if (match != null) {
                    val (filePath, lineNum, msg) = match.destructured
                    SecilcError(
                        file    = File(filePath).name,
                        line    = lineNum.toIntOrNull(),
                        message = msg.trim(),
                        rawLine = line
                    )
                } else {
                    SecilcError(
                        file    = "",
                        line    = null,
                        message = line.trim(),
                        rawLine = line
                    )
                }
            }
    }
}

package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.util.FileUtil
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.domain.path.PolicyPartition
import com.selinuxtoolbox.core.model.ActiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// ── Result types ─────────────────────────────────────────────────────────────

data class MissingAttributeResult(
    val missingAttributes: List<String>,
    val totalAttributeSets: Int,
    val filteredVersioned: Int,
    val partitions: Map<PolicyPartition, List<String>>
)

sealed class FixAttributesResult {
    data class Success(
        val outputDir: String,
        val allCilFile: File,
        val partitionFiles: Map<PolicyPartition, File>,
        val missingCount: Int
    ) : FixAttributesResult()

    data class NoOutputsFound(
        val workPath: String,
        val message: String = "No generated CIL files found in work/outputs/"
    ) : FixAttributesResult()

    data class SetupError(val reason: String) : FixAttributesResult()
}

// ── Use Case ─────────────────────────────────────────────────────────────────

@Singleton
class FixMissingAttributesUseCase @Inject constructor(
    private val pathResolver: PathResolver
) {

    // ── Public API ──────────────────────────────────────────────────────────

    suspend operator fun invoke(
        workPath: String,
        aospPath: String,
        mode: ActiveMode = ActiveMode.OFFLINE
    ): FixAttributesResult = withContext(Dispatchers.IO) {
        val outputsDir = File(workPath, "outputs")
        if (!outputsDir.exists() || outputsDir.listFiles()?.none { it.isFile && it.extension == "cil" } == true) {
            return@withContext FixAttributesResult.NoOutputsFound(workPath)
        }

        // 1. Scan all generated .cil files for typeattributeset lines
        val attributeSets = mutableMapOf<String, MutableSet<String>>() // attr -> list of files where used
        val attrToPartition = mutableMapOf<String, PolicyPartition?>()

        outputsDir.walkTopDown()
            .filter { it.isFile && it.extension == "cil" }
            .forEach { file ->
                val partition = inferPartitionFromPath(file.absolutePath, aospPath)
                file.readLines().forEachIndexed { lineNum, line ->
                    if (line.trim().startsWith("(typeattributeset")) {
                        // Parse: (typeattributeset ATTR (...))
                        val tokens = line.trim().split(Regex("\\s+"))
                        if (tokens.size >= 2) {
                            val attr = tokens[1]
                            attributeSets.getOrPut(attr) { mutableSetOf() }.add(file.absolutePath)
                            if (attr !in attrToPartition) {
                                attrToPartition[attr] = partition
                            }
                        }
                    }
                }
            }

        if (attributeSets.isEmpty()) {
            return@withContext FixAttributesResult.NoOutputsFound(workPath, "No typeattributeset lines found in generated CIL files")
        }

        // 2. Collect all existing typeattribute declarations from AOSP
        val existingAttributes = mutableSetOf<String>()
        PolicyPartition.values().forEach { partition ->
            pathResolver.allCilFiles(aospPath, partition, mode).forEach { file ->
                if (file.exists()) {
                    file.readLines().forEach { line ->
                        if (line.trim().startsWith("(typeattribute")) {
                            // Parse: (typeattribute ATTR)
                            val tokens = line.trim().split(Regex("\\s+"))
                            if (tokens.size >= 2) {
                                existingAttributes.add(tokens[1])
                            }
                        }
                    }
                }
            }
        }

        // 3. Find missing attributes (used but not declared)
        val missing = attributeSets.keys.filter { attr ->
            // Filter versioned aliases
            !isVersionedAlias(attr) && attr !in existingAttributes
        }

        // 4. Group missing attributes by partition (use first occurrence, fallback to VENDOR)
        val byPartition = missing.groupBy { attr ->
            attrToPartition[attr] ?: PolicyPartition.VENDOR
        }

        // 5. Generate output
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outDir = File(workPath, "outputs/missing_attributes_$ts")
        outDir.mkdirs()

        val allCilFile = File(outDir, "all_missing_attributes.cil")
        val partitionFiles = mutableMapOf<PolicyPartition, File>()

        val allContent = StringBuilder()
        val perPartitionContent = byPartition.mapValues { (partition, attrs) ->
            val sb = StringBuilder()
            sb.appendLine("; ── Missing attributes for ${partition.dirName} ──")
            sb.appendLine("; Generated by SELinux Toolbox – Typeattribute Fixer")
            sb.appendLine("; Total: ${attrs.size} attribute(s)")
            sb.appendLine()
            attrs.forEach { attr ->
                sb.appendLine("(typeattribute $attr)")
            }
            sb.toString()
        }

        // Write per-partition files
        perPartitionContent.forEach { (partition, content) ->
            val file = File(outDir, "${partition.dirName}_attributes.cil")
            file.writeText(content)
            partitionFiles[partition] = file
            allContent.append(content).append("\n")
        }

        // Write combined file
        allCilFile.writeText(allContent.toString())

        // Write INSTRUCTIONS.txt
        val instructions = File(outDir, "INSTRUCTIONS.txt")
        instructions.writeText(buildInstructions(missing, byPartition, outDir))

        FixAttributesResult.Success(
            outputDir = outDir.absolutePath,
            allCilFile = allCilFile,
            partitionFiles = partitionFiles,
            missingCount = missing.size
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun isVersionedAlias(attr: String): Boolean {
        return listOf(
            Regex("""^base_typeattr_\d"""),
            Regex("""_\d+_\d+$"""),
            Regex("""_20\d{4}$""")
        ).any { it.containsMatchIn(attr) }
    }

    private fun inferPartitionFromPath(filePath: String, aospPath: String): PolicyPartition? {
        val relative = filePath.removePrefix(aospPath).trimStart('/')
        return PolicyPartition.values().firstOrNull { partition ->
            relative.startsWith("${partition.dirName}/") ||
            relative.startsWith(partition.dirName)
        }
    }

    private fun buildInstructions(
        missing: List<String>,
        byPartition: Map<PolicyPartition, List<String>>,
        outDir: File
    ): String = buildString {
        appendLine("=" .repeat(70))
        appendLine("SELinux Toolbox – Missing Typeattribute Fixer")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("=" .repeat(70))
        appendLine()
        appendLine("⚠ BACKUP YOUR DEVICE BEFORE FLASHING ANYTHING.")
        appendLine("⚠ ALWAYS HAVE A BOOTLOOP RECOVERY PLAN (TWRP/fastboot).")
        appendLine()
        appendLine("Missing attributes: ${missing.size}")
        appendLine()
        appendLine("Files generated:")
        appendLine()
        byPartition.forEach { (partition, attrs) ->
            val fileName = "${partition.dirName}_attributes.cil"
            appendLine("  $fileName")
            appendLine("    → ${attrs.size} attribute(s)")
            appendLine("    → Destination: AOSP/${partition.dirName}/selinux/ (as additional file)")
            appendLine("    → Action:      NEW FILE (place in the selinux directory)")
            appendLine()
        }
        appendLine("  all_missing_attributes.cil")
        appendLine("    → Destination: AOSP/vendor/selinux/")
        appendLine("    → Action:      NEW FILE")
        appendLine("    → Note:        Load this file BEFORE vendor_sepolicy.cil")
        appendLine()
        appendLine("=" .repeat(70))
        appendLine("Load order:")
        appendLine("  1. AOSP/system/selinux/plat_sepolicy.cil")
        appendLine("  2. mapping files")
        appendLine("  3. AOSP/system_ext/selinux/system_ext_sepolicy.cil")
        appendLine("  4. AOSP/product/selinux/product_sepolicy.cil")
        appendLine("  5. AOSP/vendor/selinux/plat_pub_versioned.cil")
        appendLine("  6. all_missing_attributes.cil   ← INSERT HERE")
        appendLine("  7. AOSP/vendor/selinux/vendor_sepolicy.cil")
        appendLine()
        appendLine("Make sure all_missing_attributes.cil is loaded BEFORE vendor_sepolicy.cil.")
        appendLine("If you're using secilc, add it to the command line before vendor_sepolicy.cil.")
        appendLine("=" .repeat(70))
    }
}

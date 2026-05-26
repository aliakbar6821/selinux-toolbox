package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.analyzer.CilGenerator
import com.selinuxtoolbox.core.domain.analyzer.SafetyConfig
import com.selinuxtoolbox.core.domain.analyzer.TypeSafety
import com.selinuxtoolbox.core.domain.parser.CilParser
import com.selinuxtoolbox.core.domain.parser.ContextFileParser
import com.selinuxtoolbox.core.domain.parser.RcFileParser
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.domain.path.PolicyPartition
import com.selinuxtoolbox.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// ── Result types ─────────────────────────────────────────────────────────────

data class TypeSearchResult(
    val typeName: String,
    val inAosp: Boolean,
    val aospSources: List<String>,
    val inOem: Boolean,
    val oemSource: String?,
    val oemDeclaration: String?,
    val inContexts: List<ContextMatch>,
    val inRcFiles: List<RcMatch>,
    val safety: TypeSafety,
    val suggestedCil: String,
    val canGenerate: Boolean
)

data class ContextMatch(
    val pattern: String,
    val context: String,
    val fileType: String,
    val sourceFile: String,
    val partition: String
)

data class RcMatch(
    val serviceName: String,
    val execPath: String,
    val rcFile: String,
    val lineNumber: Int
)

sealed class ManualSearchResult {
    data class Found(val result: TypeSearchResult) : ManualSearchResult()
    data class NotFound(val typeName: String, val message: String = "Type not found in OEM or AOSP") : ManualSearchResult()
    data class Error(val reason: String) : ManualSearchResult()
}

sealed class GenerateFixResult {
    data class Success(val outputDir: String, val cilFile: File) : GenerateFixResult()
    data class SetupError(val reason: String) : GenerateFixResult()
}

// ── Use Case ─────────────────────────────────────────────────────────────────

@Singleton
class ManualTypeSearchUseCase @Inject constructor(
    private val pathResolver: PathResolver,
    private val contextParser: ContextFileParser,
    private val rcParser: RcFileParser
) {

    suspend fun search(
        typeName: String,
        oemPath: String,
        aospPath: String
    ): ManualSearchResult = withContext(Dispatchers.IO) {

        val cleaned = typeName.trim()
        if (cleaned.isEmpty()) {
            return@withContext ManualSearchResult.Error("Type name cannot be empty")
        }

        // ── 1. Search in AOSP ─────────────────────────────────────────────
        val aospSources = mutableListOf<String>()
        var inAosp = false

        PolicyPartition.values().forEach { partition ->
            pathResolver.allCilFiles(aospPath, partition, ActiveMode.OFFLINE).forEach { file ->
                if (file.exists()) {
                    val content = file.readText()
                    val parser = CilParser(file.absolutePath, partition.toPartition())
                    val statements = parser.parse(content)
                    val hasType = statements.any {
                        it is CilStatement.TypeDeclaration && it.name == cleaned
                    }
                    if (hasType) {
                        inAosp = true
                        aospSources.add(file.absolutePath)
                    }
                }
            }
        }

        // ── 2. Search in OEM ──────────────────────────────────────────────
        var inOem = false
        var oemSource: String? = null
        var oemDeclaration: String? = null

        PolicyPartition.values().forEach { partition ->
            pathResolver.allCilFiles(oemPath, partition, ActiveMode.OFFLINE).forEach { file ->
                if (file.exists()) {
                    val content = file.readText()
                    val parser = CilParser(file.absolutePath, partition.toPartition())
                    val statements = parser.parse(content)
                    val decl = statements.firstOrNull {
                        it is CilStatement.TypeDeclaration && it.name == cleaned
                    }
                    if (decl != null) {
                        inOem = true
                        oemSource = file.absolutePath
                        // Reconstruct the declaration line
                        val line = content.lines().getOrNull(decl.lineNumber - 1) ?: "(type $cleaned)"
                        oemDeclaration = line.trim()
                        return@forEach
                    }
                }
            }
            if (inOem) return@forEach
        }

        // ── 3. Search in OEM context files ──────────────────────────────
        val contextMatches = mutableListOf<ContextMatch>()

        PolicyPartition.values().forEach { partition ->
            listOf(
                ContextFileType.FILE to pathResolver.fileContexts(oemPath, partition, ActiveMode.OFFLINE),
                ContextFileType.PROPERTY to pathResolver.propertyContexts(oemPath, partition, ActiveMode.OFFLINE),
                ContextFileType.SERVICE to pathResolver.serviceContexts(oemPath, partition, ActiveMode.OFFLINE),
                ContextFileType.HWSERVICE to pathResolver.hwserviceContexts(oemPath, partition, ActiveMode.OFFLINE)
            ).forEach { (fileType, file) ->
                file?.let { f ->
                    if (f.exists()) {
                        val entries = contextParser.parse(
                            f.readText(),
                            f.absolutePath,
                            fileType,
                            partition.toPartition()
                        )
                        entries.filter { it.type == cleaned }.forEach { entry ->
                            contextMatches.add(
                                ContextMatch(
                                    pattern = entry.pattern,
                                    context = entry.context,
                                    fileType = fileType.name,
                                    sourceFile = f.absolutePath,
                                    partition = partition.dirName
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── 4. Search in OEM RC files ────────────────────────────────────
        val rcMatches = mutableListOf<RcMatch>()

        PolicyPartition.values().forEach { partition ->
            pathResolver.allRcFiles(oemPath, partition, ActiveMode.OFFLINE).forEach { rcFile ->
                val parsed = rcParser.parse(rcFile.readText(), rcFile.absolutePath)
                parsed.services.forEach { service ->
                    service.seclabel?.let { seclabel ->
                        val domain = extractDomain(seclabel)
                        if (domain == cleaned) {
                            rcMatches.add(
                                RcMatch(
                                    serviceName = service.name,
                                    execPath = service.executable,
                                    rcFile = rcFile.absolutePath,
                                    lineNumber = service.lineNumber
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── 5. Safety classification ─────────────────────────────────────
        val safety = SafetyConfig.classify(cleaned)
        val canGenerate = safety != TypeSafety.UNSAFE && !inAosp

        // ── 6. Generate suggested CIL ────────────────────────────────────
        val partition = if (inOem) {
            // Use partition from OEM source if available, else VENDOR
            oemSource?.let { src ->
                PolicyPartition.values().firstOrNull { src.contains("/${it.dirName}/") }
            } ?: PolicyPartition.VENDOR
        } else {
            PolicyPartition.VENDOR
        }

        val suggestedCil = if (canGenerate) {
            CilGenerator.fullDomainBlock(cleaned, "/system/bin/${cleaned}", partition)
        } else {
            "# Cannot generate: ${if (inAosp) "type already exists in AOSP" else "type is UNSAFE"}"
        }

        val result = TypeSearchResult(
            typeName = cleaned,
            inAosp = inAosp,
            aospSources = aospSources,
            inOem = inOem,
            oemSource = oemSource,
            oemDeclaration = oemDeclaration,
            inContexts = contextMatches,
            inRcFiles = rcMatches,
            safety = safety,
            suggestedCil = suggestedCil,
            canGenerate = canGenerate
        )

        if (!inAosp && !inOem && contextMatches.isEmpty() && rcMatches.isEmpty()) {
            ManualSearchResult.NotFound(cleaned)
        } else {
            ManualSearchResult.Found(result)
        }
    }

    suspend fun generateFix(
        typeName: String,
        workPath: String,
        partition: PolicyPartition = PolicyPartition.VENDOR
    ): GenerateFixResult = withContext(Dispatchers.IO) {

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outDir = File(workPath, "outputs/manual_fix_$ts")
        outDir.mkdirs()

        val cilFile = File(outDir, "${typeName}.cil")
        val content = CilGenerator.fullDomainBlock(typeName, "/system/bin/${typeName}", partition)
        cilFile.writeText(content)

        // INSTRUCTIONS.txt
        val instructions = File(outDir, "INSTRUCTIONS.txt")
        instructions.writeText(buildInstructions(typeName, outDir, partition))

        GenerateFixResult.Success(outDir.absolutePath, cilFile)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun extractDomain(seclabel: String): String? {
        val parts = seclabel.trim().split(":")
        return if (parts.size >= 3) parts[2].trim() else null
    }

    private fun buildInstructions(typeName: String, outDir: File, partition: PolicyPartition): String = buildString {
        appendLine("=" .repeat(70))
        appendLine("SELinux Toolbox – Manual Type Fix")
        appendLine("Type: $typeName")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("=" .repeat(70))
        appendLine()
        appendLine("Generated CIL file: $typeName.cil")
        appendLine()
        appendLine("Destination: AOSP/${partition.dirName}/selinux/")
        appendLine("Action: NEW FILE (place in the selinux directory)")
        appendLine()
        appendLine("Content:")
        appendLine(CilGenerator.fullDomainBlock(typeName, "/system/bin/${typeName}", partition))
        appendLine()
        appendLine("If you also need a file_contexts entry, add:")
        appendLine("/system/bin/${typeName}    u:object_r:${typeName}_exec:s0")
        appendLine()
        appendLine("=" .repeat(70))
        appendLine("⚠ BACKUP YOUR DEVICE BEFORE FLASHING ANYTHING.")
        appendLine("⚠ ALWAYS HAVE A BOOTLOOP RECOVERY PLAN (TWRP/fastboot).")
        appendLine("=" .repeat(70))
    }

    private fun PolicyPartition.toPartition(): Partition = when (this) {
        PolicyPartition.SYSTEM -> Partition.SYSTEM
        PolicyPartition.SYSTEM_EXT -> Partition.SYSTEM_EXT
        PolicyPartition.PRODUCT -> Partition.PRODUCT
        PolicyPartition.VENDOR -> Partition.VENDOR
        PolicyPartition.ODM -> Partition.ODM
    }
}

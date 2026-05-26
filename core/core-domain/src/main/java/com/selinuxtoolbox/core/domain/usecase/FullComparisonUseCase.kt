package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.root.RootFileReader
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// ── Result types ─────────────────────────────────────────────────────────────

@Serializable
data class ComparisonReport(
    val summary: ComparisonSummary,
    val missingTypes: List<MissingType>,
    val missingContextEntries: List<MissingContextEntry>,
    val missingSeclabels: List<MissingSeclabel>,
    val missingAttributes: List<MissingAttribute>
)

@Serializable
data class ComparisonSummary(
    val totalMissingTypes: Int,
    val totalMissingContexts: Int,
    val totalMissingSeclabels: Int,
    val totalMissingAttributes: Int,
    val safeCount: Int,
    val reviewCount: Int,
    val unsafeCount: Int,
    val byPartition: Map<String, PartitionSummary>
)

@Serializable
data class PartitionSummary(
    val missingTypes: Int,
    val missingContexts: Int,
    val missingSeclabels: Int,
    val missingAttributes: Int
)

@Serializable
data class MissingType(
    val name: String,
    val partition: String,
    val safety: TypeSafety,
    val oemSourceFile: String,
    val aospSourceFile: String?,
    val isMapping: Boolean
)

@Serializable
data class MissingContextEntry(
    val pattern: String,
    val context: String,
    val type: String,
    val fileType: String,
    val partition: String,
    val oemSourceFile: String,
    val safety: TypeSafety
)

@Serializable
data class MissingSeclabel(
    val domain: String,
    val serviceName: String,
    val execPath: String,
    val rcFile: String,
    val lineNumber: Int,
    val partition: String,
    val safety: TypeSafety
)

@Serializable
data class MissingAttribute(
    val name: String,
    val partition: String,
    val oemSourceFile: String,
    val usedInGeneratedCil: Boolean
)

sealed class FullComparisonResult {
    data class Success(
        val outputDir: String,
        val report: ComparisonReport,
        val summaryFile: File,
        val reportJsonFile: File
    ) : FullComparisonResult()

    data class SetupError(val reason: String) : FullComparisonResult()
}

// ── Use Case ─────────────────────────────────────────────────────────────────

@Singleton
class FullComparisonUseCase @Inject constructor(
    private val pathResolver: PathResolver,
    private val rootFileReader: RootFileReader,
    private val contextParser: ContextFileParser,
    private val rcParser: RcFileParser
) {

    private val json = Json { prettyPrint = true }

    suspend operator fun invoke(
        oemPath: String,
        aospPath: String,
        workPath: String
    ): FullComparisonResult = withContext(Dispatchers.IO) {

        val oemDir = File(oemPath)
        val aospDir = File(aospPath)
        if (!oemDir.exists() || !aospDir.exists()) {
            return@withContext FullComparisonResult.SetupError(
                "OEM or AOSP folder does not exist"
            )
        }

        // ── 1. Collect all AOSP types, attributes, context entries ────────
        val aospTypes = mutableSetOf<String>()
        val aospAttributes = mutableSetOf<String>()
        val aospContexts = mutableSetOf<ContextEntry>()

        PolicyPartition.values().forEach { partition ->
            // Types from CIL
            pathResolver.allCilFiles(aospPath, partition, ActiveMode.OFFLINE).forEach { file ->
                val parser = CilParser(file.absolutePath, partition.toPartition())
                val statements = parser.parse(file.readText())
                statements.forEach { stmt ->
                    when (stmt) {
                        is CilStatement.TypeDeclaration -> aospTypes.add(stmt.name)
                        is CilStatement.TypeAttribute -> aospAttributes.add(stmt.name)
                        else -> {}
                    }
                }
            }

            // Context entries
            pathResolver.fileContexts(aospPath, partition, ActiveMode.OFFLINE)?.let { file ->
                if (file.exists()) {
                    aospContexts.addAll(
                        contextParser.parse(
                            file.readText(),
                            file.absolutePath,
                            ContextFileType.FILE,
                            partition.toPartition()
                        )
                    )
                }
            }
            pathResolver.propertyContexts(aospPath, partition, ActiveMode.OFFLINE)?.let { file ->
                if (file.exists()) {
                    aospContexts.addAll(
                        contextParser.parse(
                            file.readText(),
                            file.absolutePath,
                            ContextFileType.PROPERTY,
                            partition.toPartition()
                        )
                    )
                }
            }
            pathResolver.serviceContexts(aospPath, partition, ActiveMode.OFFLINE)?.let { file ->
                if (file.exists()) {
                    aospContexts.addAll(
                        contextParser.parse(
                            file.readText(),
                            file.absolutePath,
                            ContextFileType.SERVICE,
                            partition.toPartition()
                        )
                    )
                }
            }
            pathResolver.hwserviceContexts(aospPath, partition, ActiveMode.OFFLINE)?.let { file ->
                if (file.exists()) {
                    aospContexts.addAll(
                        contextParser.parse(
                            file.readText(),
                            file.absolutePath,
                            ContextFileType.HWSERVICE,
                            partition.toPartition()
                        )
                    )
                }
            }
        }

        // ── 2. Collect OEM types, attributes, context entries, seclabels ──
        val oemTypes = mutableMapOf<String, Pair<String, PolicyPartition>>() // name -> (sourceFile, partition)
        val oemAttributes = mutableMapOf<String, Pair<String, PolicyPartition>>()
        val oemContexts = mutableListOf<ContextEntry>()
        val oemSeclabels = mutableListOf<MissingSeclabel>()

        PolicyPartition.values().forEach { partition ->
            // Types from CIL
            pathResolver.allCilFiles(oemPath, partition, ActiveMode.OFFLINE).forEach { file ->
                val parser = CilParser(file.absolutePath, partition.toPartition())
                val statements = parser.parse(file.readText())
                statements.forEach { stmt ->
                    when (stmt) {
                        is CilStatement.TypeDeclaration -> {
                            oemTypes[stmt.name] = file.absolutePath to partition
                        }
                        is CilStatement.TypeAttribute -> {
                            oemAttributes[stmt.name] = file.absolutePath to partition
                        }
                        else -> {}
                    }
                }
            }

            // Context entries
            listOf(
                ContextFileType.FILE to pathResolver.fileContexts(oemPath, partition, ActiveMode.OFFLINE),
                ContextFileType.PROPERTY to pathResolver.propertyContexts(oemPath, partition, ActiveMode.OFFLINE),
                ContextFileType.SERVICE to pathResolver.serviceContexts(oemPath, partition, ActiveMode.OFFLINE),
                ContextFileType.HWSERVICE to pathResolver.hwserviceContexts(oemPath, partition, ActiveMode.OFFLINE)
            ).forEach { (fileType, file) ->
                file?.let { f ->
                    if (f.exists()) {
                        oemContexts.addAll(
                            contextParser.parse(
                                f.readText(),
                                f.absolutePath,
                                fileType,
                                partition.toPartition()
                            )
                        )
                    }
                }
            }

            // Seclabels from RC files
            pathResolver.allRcFiles(oemPath, partition, ActiveMode.OFFLINE).forEach { rcFile ->
                val parsed = rcParser.parse(rcFile.readText(), rcFile.absolutePath)
                parsed.services.forEach { service ->
                    service.seclabel?.let { seclabel ->
                        val domain = extractDomain(seclabel)
                        if (domain != null) {
                            oemSeclabels.add(
                                MissingSeclabel(
                                    domain = domain,
                                    serviceName = service.name,
                                    execPath = service.executable,
                                    rcFile = rcFile.absolutePath,
                                    lineNumber = service.lineNumber,
                                    partition = partition.dirName,
                                    safety = SafetyConfig.classify(domain)
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── 3. Compute missing items ──────────────────────────────────────

        // Types missing in AOSP
        val missingTypes = oemTypes.keys
            .filter { it !in aospTypes }
            .map { typeName ->
                val (sourceFile, partition) = oemTypes[typeName]!!
                MissingType(
                    name = typeName,
                    partition = partition.dirName,
                    safety = SafetyConfig.classify(typeName),
                    oemSourceFile = sourceFile,
                    aospSourceFile = null,
                    isMapping = sourceFile.contains("/mapping/")
                )
            }.sortedBy { it.name }

        // Context entries missing in AOSP
        val missingContexts = oemContexts
            .filter { oemCtx ->
                aospContexts.none { aospCtx ->
                    aospCtx.pattern == oemCtx.pattern &&
                    aospCtx.type == oemCtx.type &&
                    aospCtx.fileType == oemCtx.fileType
                }
            }
            .map { ctx ->
                MissingContextEntry(
                    pattern = ctx.pattern,
                    context = ctx.context,
                    type = ctx.type,
                    fileType = ctx.fileType.name,
                    partition = ctx.partition.name,
                    oemSourceFile = ctx.sourceFile,
                    safety = SafetyConfig.classify(ctx.type)
                )
            }.sortedBy { it.type }

        // Seclabels missing in AOSP
        val missingSeclabels = oemSeclabels
            .filter { it.domain !in aospTypes }
            .sortedBy { it.domain }

        // Attributes missing in AOSP
        val missingAttributes = oemAttributes.keys
            .filter { it !in aospAttributes && !isVersionedAlias(it) }
            .map { attrName ->
                val (sourceFile, partition) = oemAttributes[attrName]!!
                // Check if used in generated CIL
                val usedInGenerated = checkGeneratedCilUsage(workPath, attrName)
                MissingAttribute(
                    name = attrName,
                    partition = partition.dirName,
                    oemSourceFile = sourceFile,
                    usedInGeneratedCil = usedInGenerated
                )
            }.sortedBy { it.name }

        // ── 4. Build summary ──────────────────────────────────────────────

        val safeCount = missingTypes.count { it.safety == TypeSafety.SAFE } +
                        missingContexts.count { it.safety == TypeSafety.SAFE } +
                        missingSeclabels.count { it.safety == TypeSafety.SAFE }
        val reviewCount = missingTypes.count { it.safety == TypeSafety.REVIEW } +
                          missingContexts.count { it.safety == TypeSafety.REVIEW } +
                          missingSeclabels.count { it.safety == TypeSafety.REVIEW }
        val unsafeCount = missingTypes.count { it.safety == TypeSafety.UNSAFE } +
                          missingContexts.count { it.safety == TypeSafety.UNSAFE } +
                          missingSeclabels.count { it.safety == TypeSafety.UNSAFE }

        val byPartition = mutableMapOf<String, PartitionSummary>()
        PolicyPartition.values().forEach { partition ->
            val pName = partition.dirName
            byPartition[pName] = PartitionSummary(
                missingTypes = missingTypes.count { it.partition == pName },
                missingContexts = missingContexts.count { it.partition == pName },
                missingSeclabels = missingSeclabels.count { it.partition == pName },
                missingAttributes = missingAttributes.count { it.partition == pName }
            )
        }

        val summary = ComparisonSummary(
            totalMissingTypes = missingTypes.size,
            totalMissingContexts = missingContexts.size,
            totalMissingSeclabels = missingSeclabels.size,
            totalMissingAttributes = missingAttributes.size,
            safeCount = safeCount,
            reviewCount = reviewCount,
            unsafeCount = unsafeCount,
            byPartition = byPartition
        )

        val report = ComparisonReport(
            summary = summary,
            missingTypes = missingTypes,
            missingContextEntries = missingContexts,
            missingSeclabels = missingSeclabels,
            missingAttributes = missingAttributes
        )

        // ── 5. Write output ─────────────────────────────────────────────────

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outDir = File(workPath, "outputs/full_comparison_$ts")
        outDir.mkdirs()

        // Summary.txt
        val summaryFile = File(outDir, "SUMMARY.txt")
        summaryFile.writeText(buildSummaryText(report))

        // Per-category files
        writeCategoryFile(outDir, "missing_types.txt", missingTypes) { it.name }
        writeCategoryFile(outDir, "missing_contexts.txt", missingContexts) {
            "${it.pattern} → ${it.type} (${it.fileType})"
        }
        writeCategoryFile(outDir, "missing_seclabels.txt", missingSeclabels) {
            "${it.domain} (${it.serviceName})"
        }
        writeCategoryFile(outDir, "missing_attributes.txt", missingAttributes) {
            it.name
        }

        // JSON report
        val reportJsonFile = File(outDir, "report.json")
        reportJsonFile.writeText(json.encodeToString(report))

        // INSTRUCTIONS.txt
        val instructions = File(outDir, "INSTRUCTIONS.txt")
        instructions.writeText(buildInstructions(report, outDir))

        FullComparisonResult.Success(
            outputDir = outDir.absolutePath,
            report = report,
            summaryFile = summaryFile,
            reportJsonFile = reportJsonFile
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun extractDomain(seclabel: String): String? {
        val parts = seclabel.trim().split(":")
        return if (parts.size >= 3) parts[2].trim() else null
    }

    private fun isVersionedAlias(attr: String): Boolean {
        return listOf(
            Regex("""^base_typeattr_\d"""),
            Regex("""_\d+_\d+$"""),
            Regex("""_20\d{4}$""")
        ).any { it.containsMatchIn(attr) }
    }

    private fun checkGeneratedCilUsage(workPath: String, attrName: String): Boolean {
        val outputsDir = File(workPath, "outputs")
        if (!outputsDir.exists()) return false
        return outputsDir.walkTopDown()
            .filter { it.isFile && it.extension == "cil" }
            .any { file ->
                file.readLines().any { line ->
                    line.contains("(typeattributeset $attrName") ||
                    line.contains("(typeattributeset $attrName ")
                }
            }
    }

    private fun <T> writeCategoryFile(
        outDir: File,
        fileName: String,
        items: List<T>,
        lineMapper: (T) -> String
    ) {
        val file = File(outDir, fileName)
        file.writeText(items.joinToString("\n") { lineMapper(it) })
    }

    private fun buildSummaryText(report: ComparisonReport): String = buildString {
        appendLine("=" .repeat(70))
        appendLine("SELinux Toolbox – Full OEM vs AOSP Comparison Report")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("=" .repeat(70))
        appendLine()
        appendLine("SUMMARY")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine("Missing types:         ${report.summary.totalMissingTypes}")
        appendLine("Missing context entries: ${report.summary.totalMissingContexts}")
        appendLine("Missing seclabels:      ${report.summary.totalMissingSeclabels}")
        appendLine("Missing attributes:     ${report.summary.totalMissingAttributes}")
        appendLine()
        appendLine("Safety classification:")
        appendLine("  SAFE   : ${report.summary.safeCount}")
        appendLine("  REVIEW : ${report.summary.reviewCount}")
        appendLine("  UNSAFE : ${report.summary.unsafeCount}")
        appendLine()
        appendLine("By partition:")
        report.summary.byPartition.forEach { (partition, stats) ->
            appendLine("  $partition:")
            appendLine("    Types:     ${stats.missingTypes}")
            appendLine("    Contexts:  ${stats.missingContexts}")
            appendLine("    Seclabels: ${stats.missingSeclabels}")
            appendLine("    Attributes: ${stats.missingAttributes}")
            appendLine()
        }
        appendLine("=" .repeat(70))
        appendLine("See individual .txt files for full lists.")
        appendLine("Use report.json for machine-readable analysis.")
        appendLine("=" .repeat(70))
    }

    private fun buildInstructions(report: ComparisonReport, outDir: File): String = buildString {
        appendLine("=" .repeat(70))
        appendLine("SELinux Toolbox – Full OEM vs AOSP Comparison Report")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("=" .repeat(70))
        appendLine()
        appendLine("This report shows everything present in OEM (ColorOS) that is")
        appendLine("missing from AOSP (Moto) SELinux policy.")
        appendLine()
        appendLine("Files generated:")
        appendLine("  SUMMARY.txt                    → overview counts")
        appendLine("  missing_types.txt              → types missing from AOSP")
        appendLine("  missing_contexts.txt           → context entries missing")
        appendLine("  missing_seclabels.txt          → seclabels not in AOSP")
        appendLine("  missing_attributes.txt         → typeattributes missing")
        appendLine("  report.json                    → machine-readable data")
        appendLine("  INSTRUCTIONS.txt              → this file")
        appendLine()
        appendLine("NEXT STEPS:")
        appendLine()
        appendLine("1. Run RC Seclabel Scanner (Feature 1) to generate missing domains")
        appendLine("2. Run Context Diff (Feature 2) to generate missing context entries")
        appendLine("3. Run Missing Typeattribute Fixer (Feature 4) to add missing attributes")
        appendLine("4. Review and apply fixes in order")
        appendLine()
        appendLine("Safety classification guide:")
        appendLine("  SAFE   → auto-generate with bulk confirm")
        appendLine("  REVIEW → requires per-item confirmation")
        appendLine("  UNSAFE → never auto-generate (core system types)")
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

package com.selinuxtoolbox.core.domain.analyzer

import com.selinuxtoolbox.core.domain.parser.CilParser
import com.selinuxtoolbox.core.domain.parser.RcFileParser
import com.selinuxtoolbox.core.domain.parser.RcService
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.domain.path.PolicyPartition
import com.selinuxtoolbox.core.model.ActiveMode
import com.selinuxtoolbox.core.model.CilStatement
import com.selinuxtoolbox.core.model.Partition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// One RC service entry with its domain and classification
data class SeclabelEntry(
    val serviceName: String,
    val domainName: String,        // extracted from seclabel u:r:DOMAIN:s0
    val execPath: String,          // binary path from service line
    val sourceFile: String,        // which .rc file
    val lineNumber: Int,
    val partition: PolicyPartition,
    val safety: TypeSafety,
    val existsInAosp: Boolean,     // already declared in AOSP CIL?
    val existsInOem: Boolean       // declared in OEM CIL (for reference)?
)

// Scan result returned to use case / ViewModel
data class RcScanResult(
    val allEntries: List<SeclabelEntry>,
    // Only entries that are MISSING from AOSP — these need CIL generation
    val missingEntries: List<SeclabelEntry>,
    val safeEntries: List<SeclabelEntry>,     // missing + SAFE
    val reviewEntries: List<SeclabelEntry>,   // missing + REVIEW
    val unsafeEntries: List<SeclabelEntry>,   // missing + UNSAFE (blocked)
    val alreadyCovered: List<SeclabelEntry>,  // already in AOSP
    val scannedRcFiles: Int,
    val scannedAospCilFiles: Int
)

// Generated output for one or more confirmed entries
data class RcScanOutput(
    val cilContent: String,           // to append to {partition}_sepolicy.cil
    val fileContextsContent: String,  // to append to {partition}_file_contexts
    val partition: PolicyPartition,
    val entryCount: Int
)

@Singleton
class RcSeclabelScanner @Inject constructor(
    private val pathResolver: PathResolver
) {

    private val rcParser  = RcFileParser()

    // ── Phase 1: Scan and classify ────────────────────────────────────────────

    suspend fun scan(
        oemPath: String,
        aospPath: String,
        mode: ActiveMode = ActiveMode.OFFLINE
    ): RcScanResult = withContext(Dispatchers.IO) {

        // 1. Collect all AOSP declared types (across all partitions)
        val aospTypes = collectDeclaredTypes(aospPath, mode)

        // 2. Collect all OEM declared types (for reference / existsInOem flag)
        val oemTypes = collectDeclaredTypes(oemPath, mode)

        // 3. Scan OEM init RC files from all partitions
        //    ODM is included for RC scanning — but we never generate ODM CIL
        val partitionsToScan = listOf(
            PolicyPartition.SYSTEM_EXT,
            PolicyPartition.PRODUCT,
            PolicyPartition.VENDOR,
            PolicyPartition.ODM
        )

        val allEntries = mutableListOf<SeclabelEntry>()
        var totalRcFiles = 0

        partitionsToScan.forEach { partition ->
            val rcFiles = pathResolver.allRcFiles(oemPath, partition, mode)
            totalRcFiles += rcFiles.size

            rcFiles.forEach { rcFile ->
                val parsed = rcParser.parse(rcFile.readText(), rcFile.absolutePath)
                parsed.services.forEach { service ->
                    val seclabel = service.seclabel ?: return@forEach
                    val domainName = extractDomain(seclabel) ?: return@forEach

                    val safety       = SafetyConfig.classify(domainName)
                    val existsInAosp = domainName in aospTypes
                    val existsInOem  = domainName in oemTypes

                    allEntries.add(
                        SeclabelEntry(
                            serviceName  = service.name,
                            domainName   = domainName,
                            execPath     = service.executable,
                            sourceFile   = service.sourceFile,
                            lineNumber   = service.lineNumber,
                            partition    = partition,
                            safety       = safety,
                            existsInAosp = existsInAosp,
                            existsInOem  = existsInOem
                        )
                    )
                }
            }
        }

        // 4. Deduplicate by domainName — keep first occurrence
        val seen = mutableSetOf<String>()
        val deduped = allEntries.filter { seen.add(it.domainName) }

        val missing       = deduped.filter { !it.existsInAosp }
        val alreadyCovered = deduped.filter { it.existsInAosp }

        RcScanResult(
            allEntries        = deduped,
            missingEntries    = missing,
            safeEntries       = missing.filter { it.safety == TypeSafety.SAFE },
            reviewEntries     = missing.filter { it.safety == TypeSafety.REVIEW },
            unsafeEntries     = missing.filter { it.safety == TypeSafety.UNSAFE },
            alreadyCovered    = alreadyCovered,
            scannedRcFiles    = totalRcFiles,
            scannedAospCilFiles = countCilFiles(aospPath, mode)
        )
    }

    // ── Phase 2: Generate CIL for confirmed entries ───────────────────────────

    fun generateOutput(
        confirmedEntries: List<SeclabelEntry>,
        workPath: String,
        operation: String = "rc_seclabels"
    ): Map<PolicyPartition, RcScanOutput> {
        // Group by partition
        val byPartition = confirmedEntries.groupBy { resolveOutputPartition(it.partition) }

        return byPartition.mapValues { (partition, entries) ->
            val cilSb  = StringBuilder()
            val fcSb   = StringBuilder()

            cilSb.appendLine("; Generated by SELinux Toolbox — RC Seclabel Scanner")
            cilSb.appendLine("; Append this to AOSP ${partition.dirName}_sepolicy.cil")
            cilSb.appendLine("; DO NOT replace the existing file — APPEND only")
            cilSb.appendLine()

            fcSb.appendLine("# Generated by SELinux Toolbox — RC Seclabel Scanner")
            fcSb.appendLine("# Append these lines to AOSP ${partition.dirName}_file_contexts")
            fcSb.appendLine()

            entries.forEach { entry ->
                // Skip ODM for CIL generation — no editable ODM CIL
                if (entry.partition == PolicyPartition.ODM) {
                    cilSb.appendLine("; SKIPPED (ODM): ${entry.domainName} — ODM has no editable CIL")
                    cilSb.appendLine("; RC file: ${entry.sourceFile}:${entry.lineNumber}")
                    cilSb.appendLine()
                    return@forEach
                }

                cilSb.append(
                    CilGenerator.fullDomainBlock(entry.domainName, entry.execPath, partition)
                )

                // Only generate file_contexts entry if we have a real exec path
                if (entry.execPath.isNotBlank() && entry.execPath.startsWith("/")) {
                    fcSb.append(
                        CilGenerator.fileContextEntry(entry.execPath, entry.domainName, isExec = true)
                    )
                } else if (entry.execPath.isNotBlank()) {
                    // Relative path — prefix with partition mount point
                    val fullPath = resolveExecPath(entry.execPath, partition)
                    fcSb.append(
                        CilGenerator.fileContextEntry(fullPath, entry.domainName, isExec = true)
                    )
                }
            }

            RcScanOutput(
                cilContent          = cilSb.toString(),
                fileContextsContent = fcSb.toString(),
                partition           = partition,
                entryCount          = entries.size
            )
        }
    }

    // Write outputs to work/outputs/{operation}_{timestamp}/
    fun writeOutputs(
        outputs: Map<PolicyPartition, RcScanOutput>,
        workPath: String,
        operation: String = "rc_seclabels"
    ): File {
        val outDir = pathResolver.outputDir(workPath, operation)
        outDir.mkdirs()

        val instructions = mutableListOf<OutputFileInstruction>()

        outputs.forEach { (partition, output) ->
            val cilFileName = "${partition.dirName}_sepolicy.cil"
            val fcFileName  = "${partition.dirName}_file_contexts"

            val cilFile = File(outDir, cilFileName)
            val fcFile  = File(outDir, fcFileName)

            cilFile.writeText(output.cilContent)
            fcFile.writeText(output.fileContextsContent)

            val aospDestPrefix = "AOSP/${partition.dirName}/selinux"
            instructions.add(
                OutputFileInstruction(
                    fileName    = cilFileName,
                    destination = "$aospDestPrefix/$cilFileName",
                    action      = "APPEND to existing file",
                    notes       = listOf("${output.entryCount} domain(s) added")
                )
            )
            instructions.add(
                OutputFileInstruction(
                    fileName    = fcFileName,
                    destination = "$aospDestPrefix/$fcFileName",
                    action      = "APPEND to existing file"
                )
            )
        }

        val instructionsTxt = File(outDir, "INSTRUCTIONS.txt")
        instructionsTxt.writeText(
            CilGenerator.instructionsTxt("RC Seclabel Scanner", instructions)
        )

        return outDir
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Extract domain name from seclabel string
    // "u:r:myservice:s0" → "myservice"
    // "myservice"        → "myservice" (bare label — rare but handle it)
    private fun extractDomain(seclabel: String): String? {
        val trimmed = seclabel.trim()
        return if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            parts.getOrNull(2)?.takeIf { it.isNotBlank() }
        } else {
            trimmed.takeIf { it.isNotBlank() }
        }
    }

    // Collect all (type NAME) declarations from all CIL files under basePath
    private fun collectDeclaredTypes(basePath: String, mode: ActiveMode): Set<String> {
        val types = mutableSetOf<String>()
        PolicyPartition.entries.forEach { partition ->
            val cilFiles = pathResolver.allCilFiles(basePath, partition, mode)
            cilFiles.forEach { file ->
                try {
                    val parser = CilParser(file.absolutePath, mapPartition(partition))
                    val statements = parser.parse(file.readText())
                    statements.filterIsInstance<CilStatement.TypeDeclaration>()
                        .forEach { types.add(it.name) }
                } catch (e: Exception) {
                    // Skip unparseable files
                }
            }
        }
        return types
    }

    private fun countCilFiles(basePath: String, mode: ActiveMode): Int =
        PolicyPartition.entries.sumOf { partition ->
            pathResolver.allCilFiles(basePath, partition, mode).size
        }

    // ODM services go into vendor CIL (ODM has no editable CIL)
    private fun resolveOutputPartition(partition: PolicyPartition): PolicyPartition =
        if (partition == PolicyPartition.ODM) PolicyPartition.VENDOR else partition

    // Convert a relative exec path to an absolute path for file_contexts
    private fun resolveExecPath(relative: String, partition: PolicyPartition): String {
        val mountPoint = when (partition) {
            PolicyPartition.SYSTEM     -> "/system"
            PolicyPartition.SYSTEM_EXT -> "/system_ext"
            PolicyPartition.PRODUCT    -> "/product"
            PolicyPartition.VENDOR     -> "/vendor"
            PolicyPartition.ODM        -> "/odm"
        }
        return if (relative.startsWith("/")) relative
        else "$mountPoint/$relative"
    }

    private fun mapPartition(p: PolicyPartition): Partition = when (p) {
        PolicyPartition.SYSTEM     -> Partition.SYSTEM
        PolicyPartition.SYSTEM_EXT -> Partition.SYSTEM_EXT
        PolicyPartition.PRODUCT    -> Partition.PRODUCT
        PolicyPartition.VENDOR     -> Partition.VENDOR
        PolicyPartition.ODM        -> Partition.ODM
    }
}

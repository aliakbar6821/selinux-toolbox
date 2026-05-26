package com.selinuxtoolbox.core.domain.analyzer

import com.selinuxtoolbox.core.model.CilFile
import com.selinuxtoolbox.core.model.CilStatement
import com.selinuxtoolbox.core.model.ContextEntry
import com.selinuxtoolbox.core.model.DenialMetadataEntry
import com.selinuxtoolbox.core.model.LoadedPolicy
import com.selinuxtoolbox.core.model.OperationProgress
import com.selinuxtoolbox.core.model.Partition
import com.selinuxtoolbox.core.model.RuleAnalysisResult
import com.selinuxtoolbox.core.model.RuleClassification
import com.selinuxtoolbox.core.model.SuggestedAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

// SoC-specific type prefixes — types with these prefixes on a non-matching
// device are classified as WRONG_SOC
private val SOC_PREFIXES = mapOf(
    "qcom_"    to "Qualcomm",
    "mtk_"     to "MediaTek",
    "exynos_"  to "Samsung Exynos",
    "tegra_"   to "NVIDIA Tegra",
    "kirin_"   to "HiSilicon Kirin",
    "unisoc_"  to "UNISOC",
    "spreadtrum_" to "UNISOC/Spreadtrum"
)

// ROM-specific type prefixes — types from a donor ROM that don't belong
// on the target ROM
private val ROM_PREFIXES = mapOf(
    "oneplus_"  to "OxygenOS",
    "oppo_"     to "ColorOS",
    "realme_"   to "realme UI",
    "vivo_"     to "OriginOS/FuntouchOS",
    "miui_"     to "MIUI",
    "hyperos_"  to "HyperOS",
    "samsung_"  to "One UI",
    "pixel_"    to "Pixel/GrapheneOS",
    "nothing_"  to "Nothing OS",
    "asus_"     to "ZenUI",
    "nokia_"    to "Nokia Android"
)

data class CleanupResult(
    val totalRules: Int,
    val results: List<RuleAnalysisResult>,
    val protectedTypes: Set<String>,
    val activeTypes: Set<String>,
    val orphanedTypes: Set<String>,
    val wrongSocTypes: Set<String>,
    val wrongRomTypes: Set<String>,
    // Map of CilFile path → cleaned content string
    val cleanedFileContents: Map<String, String>
)

@Singleton
class CleanupEngine @Inject constructor() {

    // -------------------------------------------------------------------------
    // Main entry point — returns a Flow so the UI can show progress
    // -------------------------------------------------------------------------

    fun analyze(
        policy: LoadedPolicy,
        projectSocPrefixes: List<String> = emptyList(),  // e.g. ["mtk_"] for MTK device
        projectRomPrefixes: List<String> = emptyList()   // e.g. ["hyperos_"] for target ROM
    ): Flow<Pair<OperationProgress, CleanupResult?>> = flow {

        val allCilFiles = policy.cilFiles
        val totalFiles = allCilFiles.size

        // ------------------------------------------------------------------
        // Phase 1: Build the complete type reference universe
        // ------------------------------------------------------------------
        emit(progress(0, totalFiles, "Building type universe…") to null)

        // All types declared across all files
        val allDeclaredTypes = buildDeclaredTypes(policy)

        // Types protected by mapping files — NEVER touch these
        val protectedTypes = buildProtectedTypes(policy)

        // Types referenced by running processes and file contexts
        val activeTypes = buildActiveTypes(policy)

        // Types referenced by denial metadata — advisory signal
        val denialMetadataTypes = buildDenialMetadataTypes(policy.denialMetadata)

        // Determine device SoC from vendor property or user hint
        val activeSocPrefixes = projectSocPrefixes.ifEmpty {
            inferSocPrefixes(policy)
        }
        val activeRomPrefixes = projectRomPrefixes.ifEmpty {
            inferRomPrefixes(policy)
        }

        // ------------------------------------------------------------------
        // Phase 2: Classify every statement in non-mapping CIL files
        // ------------------------------------------------------------------
        val results = mutableListOf<RuleAnalysisResult>()
        val cleanedContents = mutableMapOf<String, String>()

        allCilFiles
            .filter { !it.isMapping }  // NEVER touch mapping files
            .forEachIndexed { index, cilFile ->
                emit(
                    progress(
                        current = index + 1,
                        total = totalFiles,
                        file = cilFile.relativePath,
                        message = "Analyzing ${cilFile.relativePath}"
                    ) to null
                )

                val fileResults = analyzeFile(
                    cilFile = cilFile,
                    allDeclaredTypes = allDeclaredTypes,
                    protectedTypes = protectedTypes,
                    activeTypes = activeTypes,
                    denialMetadataTypes = denialMetadataTypes,
                    activeSocPrefixes = activeSocPrefixes,
                    activeRomPrefixes = activeRomPrefixes
                )
                results.addAll(fileResults)

                // Build cleaned content for this file
                val cleaned = buildCleanedContent(
                    cilFile = cilFile,
                    fileResults = fileResults
                )
                cleanedContents[cilFile.absolutePath] = cleaned
            }

        // ------------------------------------------------------------------
        // Phase 3: Summarize
        // ------------------------------------------------------------------
        val orphanedTypes = results
            .filter { it.classification == RuleClassification.ORPHANED }
            .mapNotNull { r ->
                when (val s = r.statement) {
                    is CilStatement.TypeDeclaration -> s.name
                    else -> null
                }
            }.toSet()

        val wrongSocTypes = results
            .filter { it.classification == RuleClassification.WRONG_SOC }
            .mapNotNull { r ->
                when (val s = r.statement) {
                    is CilStatement.TypeDeclaration -> s.name
                    else -> null
                }
            }.toSet()

        val wrongRomTypes = results
            .filter { it.classification == RuleClassification.WRONG_ROM }
            .mapNotNull { r ->
                when (val s = r.statement) {
                    is CilStatement.TypeDeclaration -> s.name
                    else -> null
                }
            }.toSet()

        emit(
            progress(totalFiles, totalFiles, "Analysis complete") to
            CleanupResult(
                totalRules = results.size,
                results = results,
                protectedTypes = protectedTypes,
                activeTypes = activeTypes,
                orphanedTypes = orphanedTypes,
                wrongSocTypes = wrongSocTypes,
                wrongRomTypes = wrongRomTypes,
                cleanedFileContents = cleanedContents
            )
        )
    }.flowOn(Dispatchers.Default)

    // -------------------------------------------------------------------------
    // Classify all statements in a single CIL file
    // -------------------------------------------------------------------------

    private fun analyzeFile(
        cilFile: CilFile,
        allDeclaredTypes: Set<String>,
        protectedTypes: Set<String>,
        activeTypes: Set<String>,
        denialMetadataTypes: Set<String>,
        activeSocPrefixes: List<String>,
        activeRomPrefixes: List<String>
    ): List<RuleAnalysisResult> {
        return cilFile.statements.map { statement ->
            classifyStatement(
                statement = statement,
                allDeclaredTypes = allDeclaredTypes,
                protectedTypes = protectedTypes,
                activeTypes = activeTypes,
                denialMetadataTypes = denialMetadataTypes,
                activeSocPrefixes = activeSocPrefixes,
                activeRomPrefixes = activeRomPrefixes
            )
        }
    }

    // -------------------------------------------------------------------------
    // Classify a single CIL statement
    // -------------------------------------------------------------------------

    private fun classifyStatement(
        statement: CilStatement,
        allDeclaredTypes: Set<String>,
        protectedTypes: Set<String>,
        activeTypes: Set<String>,
        denialMetadataTypes: Set<String>,
        activeSocPrefixes: List<String>,
        activeRomPrefixes: List<String>
    ): RuleAnalysisResult {

        // Extract all type names referenced by this statement
        val referencedTypes = extractReferencedTypes(statement)
        val primaryType = getPrimaryType(statement)

        // Rule 1: PROTECTED — referenced by mapping files, never remove
        if (referencedTypes.any { it in protectedTypes } ||
            primaryType != null && primaryType in protectedTypes) {
            return RuleAnalysisResult(
                statement = statement,
                classification = RuleClassification.PROTECTED,
                reason = "Referenced by mapping/ files — must not be removed",
                suggestedAction = SuggestedAction.KEEP
            )
        }

        // Rule 2: WRONG_SOC — type name matches a different SoC prefix
        if (primaryType != null) {
            val wrongSoc = checkWrongSoc(primaryType, activeSocPrefixes)
            if (wrongSoc != null) {
                return RuleAnalysisResult(
                    statement = statement,
                    classification = RuleClassification.WRONG_SOC,
                    reason = "Type '$primaryType' has $wrongSoc SoC prefix " +
                             "— not applicable on this device",
                    suggestedAction = SuggestedAction.REMOVE
                )
            }
        }

        // Rule 3: WRONG_ROM — type name matches a donor ROM prefix
        if (primaryType != null) {
            val wrongRom = checkWrongRom(primaryType, activeRomPrefixes)
            if (wrongRom != null) {
                return RuleAnalysisResult(
                    statement = statement,
                    classification = RuleClassification.WRONG_ROM,
                    reason = "Type '$primaryType' has $wrongRom ROM prefix " +
                             "— belongs to donor ROM, not target ROM",
                    suggestedAction = SuggestedAction.REMOVE
                )
            }
        }

        // Rule 4: Type declaration — check if it's ever referenced anywhere
        if (statement is CilStatement.TypeDeclaration) {
            val typeName = statement.name
            if (typeName !in activeTypes && typeName !in allDeclaredTypes) {
                return RuleAnalysisResult(
                    statement = statement,
                    classification = RuleClassification.ORPHANED,
                    reason = "Type '$typeName' is declared but never referenced " +
                             "in any rule, context, or process",
                    suggestedAction = SuggestedAction.COMMENT_OUT
                )
            }
            if (typeName in activeTypes) {
                return RuleAnalysisResult(
                    statement = statement,
                    classification = RuleClassification.ACTIVE,
                    reason = "Type '$typeName' is referenced by active rules/contexts",
                    suggestedAction = SuggestedAction.KEEP
                )
            }
        }

        // Rule 5: Allow rule — check if source/target types exist
        if (statement is CilStatement.AllowRule) {
            val missingTypes = referencedTypes.filter { type ->
                type != "self" &&
                !type.startsWith("~") &&
                !type.startsWith("*") &&
                type !in allDeclaredTypes &&
                type !in protectedTypes
            }
            if (missingTypes.isNotEmpty()) {
                // Check denial metadata — maybe this is intentional
                if (missingTypes.any { it in denialMetadataTypes }) {
                    return RuleAnalysisResult(
                        statement = statement,
                        classification = RuleClassification.REVIEW,
                        reason = "Allow rule references types in selinux_denial_metadata " +
                                 "(may be intentional): ${missingTypes.take(3).joinToString()}",
                        suggestedAction = SuggestedAction.COMMENT_OUT
                    )
                }
                return RuleAnalysisResult(
                    statement = statement,
                    classification = RuleClassification.ORPHANED,
                    reason = "Allow rule references undeclared types: " +
                             "${missingTypes.take(3).joinToString()}",
                    suggestedAction = SuggestedAction.COMMENT_OUT
                )
            }
        }

        // Rule 6: Generic active — all referenced types exist
        return RuleAnalysisResult(
            statement = statement,
            classification = RuleClassification.ACTIVE,
            reason = "All referenced types are declared and active",
            suggestedAction = SuggestedAction.KEEP
        )
    }

    // -------------------------------------------------------------------------
    // Build cleaned CIL content for a file
    //
    // Rules:
    //   KEEP      → output unchanged
    //   REMOVE    → skip entirely (line not written)
    //   COMMENT_OUT → prefix with "; REVIEW: <reason>"
    //   MANUAL_REVIEW → prefix with "; MANUAL_REVIEW: <reason>"
    // -------------------------------------------------------------------------

    private fun buildCleanedContent(
        cilFile: CilFile,
        fileResults: List<RuleAnalysisResult>
    ): String {
        // Re-read original content line by line.
        // We need the raw text to preserve formatting — the parsed statements
        // give us line numbers to locate each statement in the original.
        //
        // Strategy: build a map of lineNumber → action, then process line by line.
        val lineActions = mutableMapOf<Int, Pair<SuggestedAction, String>>()

        fileResults.forEach { result ->
            if (result.suggestedAction != SuggestedAction.KEEP) {
                lineActions[result.statement.lineNumber] =
                    result.suggestedAction to result.reason
            }
        }

        // If nothing to change, return original content marker
        // (caller will use original file)
        if (lineActions.isEmpty()) return UNCHANGED_MARKER

        // We don't have the raw text here — the policy was loaded in the
        // repository. Return a structured patch descriptor instead.
        // The use case will apply this against the actual file content.
        return buildPatchDescriptor(fileResults)
    }

    private fun buildPatchDescriptor(results: List<RuleAnalysisResult>): String {
        val sb = StringBuilder()
        results.forEach { result ->
            when (result.suggestedAction) {
                SuggestedAction.KEEP -> {} // no-op
                SuggestedAction.REMOVE -> {
                    sb.appendLine("REMOVE:${result.statement.lineNumber}")
                }
                SuggestedAction.COMMENT_OUT -> {
                    sb.appendLine(
                        "COMMENT:${result.statement.lineNumber}:${result.reason}"
                    )
                }
                SuggestedAction.MANUAL_REVIEW -> {
                    sb.appendLine(
                        "REVIEW:${result.statement.lineNumber}:${result.reason}"
                    )
                }
            }
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Apply patch descriptor to raw file content
    // Called by the use case which has the raw content available
    // -------------------------------------------------------------------------

    fun applyPatch(rawContent: String, patchDescriptor: String): String {
        if (patchDescriptor == UNCHANGED_MARKER) return rawContent

        // Parse patch descriptor
        data class PatchOp(val line: Int, val op: String, val reason: String)
        val ops = mutableMapOf<Int, PatchOp>()

        patchDescriptor.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split(":", limit = 3)
            when (parts.getOrNull(0)) {
                "REMOVE" -> {
                    val lineNum = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    ops[lineNum] = PatchOp(lineNum, "REMOVE", "")
                }
                "COMMENT" -> {
                    val lineNum = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val reason = parts.getOrNull(2) ?: ""
                    ops[lineNum] = PatchOp(lineNum, "COMMENT", reason)
                }
                "REVIEW" -> {
                    val lineNum = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val reason = parts.getOrNull(2) ?: ""
                    ops[lineNum] = PatchOp(lineNum, "REVIEW", reason)
                }
            }
        }

        if (ops.isEmpty()) return rawContent

        val outputLines = mutableListOf<String>()
        rawContent.lines().forEachIndexed { index, rawLine ->
            val lineNum = index + 1  // 1-based
            val op = ops[lineNum]
            when (op?.op) {
                "REMOVE"  -> { /* skip — do not add to output */ }
                "COMMENT" -> {
                    outputLines.add("; REVIEW: ${op.reason}")
                    outputLines.add("; $rawLine")
                }
                "REVIEW"  -> {
                    outputLines.add("; MANUAL_REVIEW: ${op.reason}")
                    outputLines.add(rawLine)
                }
                else -> outputLines.add(rawLine)
            }
        }

        return outputLines.joinToString("\n")
    }

    // -------------------------------------------------------------------------
    // Type universe builders
    // -------------------------------------------------------------------------

    private fun buildDeclaredTypes(policy: LoadedPolicy): Set<String> {
        val types = mutableSetOf<String>()
        policy.cilFiles.flatMap { it.statements }.forEach { stmt ->
            when (stmt) {
                is CilStatement.TypeDeclaration   -> types.add(stmt.name)
                is CilStatement.TypeAttribute     -> types.add(stmt.name)
                is CilStatement.TypeAttributeSet  -> {
                    types.add(stmt.attribute)
                    types.addAll(stmt.members)
                }
                else -> {}
            }
        }
        return types
    }

    private fun buildProtectedTypes(policy: LoadedPolicy): Set<String> {
        val protected = mutableSetOf<String>()
        policy.cilFiles
            .filter { it.isMapping }
            .flatMap { it.statements }
            .forEach { stmt ->
                when (stmt) {
                    is CilStatement.TypeDeclaration  -> protected.add(stmt.name)
                    is CilStatement.TypeAttribute    -> protected.add(stmt.name)
                    is CilStatement.TypeAttributeSet -> {
                        protected.add(stmt.attribute)
                        protected.addAll(stmt.members)
                    }
                    is CilStatement.AllowRule -> {
                        protected.add(stmt.source)
                        protected.add(stmt.target)
                    }
                    else -> {}
                }
            }
        return protected
    }

    private fun buildActiveTypes(policy: LoadedPolicy): Set<String> {
        val active = mutableSetOf<String>()
        // Types referenced in context files = files/services/properties
        // that exist on the live device
        policy.contextEntries.forEach { entry ->
            active.add(entry.type)
        }
        // Types referenced in allow rules in vendor/system (used = active)
        policy.cilFiles
            .filter { it.partition == Partition.VENDOR || it.partition == Partition.SYSTEM }
            .flatMap { it.statements }
            .filterIsInstance<CilStatement.AllowRule>()
            .forEach { rule ->
                active.add(rule.source)
                active.add(rule.target)
            }
        return active
    }

    private fun buildDenialMetadataTypes(metadata: List<DenialMetadataEntry>): Set<String> {
        val types = mutableSetOf<String>()
        metadata.forEach { entry ->
            types.add(entry.sourceDomain)
            types.add(entry.targetType)
        }
        return types
    }

    // -------------------------------------------------------------------------
    // SoC / ROM inference
    // -------------------------------------------------------------------------

    private fun inferSocPrefixes(policy: LoadedPolicy): List<String> {
        // Count type prefixes across vendor CIL files to find the dominant SoC
        val prefixCounts = mutableMapOf<String, Int>()
        policy.cilFiles
            .filter { it.partition == Partition.VENDOR }
            .flatMap { it.statements }
            .filterIsInstance<CilStatement.TypeDeclaration>()
            .forEach { decl ->
                SOC_PREFIXES.keys.forEach { prefix ->
                    if (decl.name.startsWith(prefix)) {
                        prefixCounts[prefix] = (prefixCounts[prefix] ?: 0) + 1
                    }
                }
            }
        // Return prefixes that are NOT dominant (i.e. foreign SoC types)
        val dominant = prefixCounts.maxByOrNull { it.value }?.key
        return SOC_PREFIXES.keys.filter { it != dominant }
    }

    private fun inferRomPrefixes(policy: LoadedPolicy): List<String> {
        // Similar logic — find dominant ROM prefix in system partition
        val prefixCounts = mutableMapOf<String, Int>()
        policy.cilFiles
            .filter { it.partition == Partition.SYSTEM }
            .flatMap { it.statements }
            .filterIsInstance<CilStatement.TypeDeclaration>()
            .forEach { decl ->
                ROM_PREFIXES.keys.forEach { prefix ->
                    if (decl.name.startsWith(prefix)) {
                        prefixCounts[prefix] = (prefixCounts[prefix] ?: 0) + 1
                    }
                }
            }
        val dominant = prefixCounts.maxByOrNull { it.value }?.key
        return ROM_PREFIXES.keys.filter { it != dominant }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun extractReferencedTypes(statement: CilStatement): List<String> {
        return when (statement) {
            is CilStatement.TypeDeclaration   -> listOf(statement.name)
            is CilStatement.TypeAttribute     -> listOf(statement.name)
            is CilStatement.TypeAttributeSet  ->
                listOf(statement.attribute) + statement.members
            is CilStatement.AllowRule         ->
                listOf(statement.source, statement.target)
            is CilStatement.NeverAllowRule    ->
                listOf(statement.source, statement.target)
            is CilStatement.AuditAllowRule    ->
                listOf(statement.source, statement.target)
            is CilStatement.DontAuditRule     ->
                listOf(statement.source, statement.target)
            is CilStatement.TypeTransition    ->
                listOf(statement.source, statement.target, statement.defaultType)
            is CilStatement.TypeChange        ->
                listOf(statement.source, statement.target, statement.defaultType)
            is CilStatement.RoleAllow         ->
                listOf(statement.source, statement.target)
            is CilStatement.GenFsCon          -> listOf(statement.context)
            is CilStatement.GenericStatement  -> emptyList()
        }
    }

    private fun getPrimaryType(statement: CilStatement): String? {
        return when (statement) {
            is CilStatement.TypeDeclaration  -> statement.name
            is CilStatement.TypeAttribute    -> statement.name
            is CilStatement.AllowRule        -> statement.source
            is CilStatement.NeverAllowRule   -> statement.source
            is CilStatement.TypeTransition   -> statement.source
            else -> null
        }
    }

    private fun checkWrongSoc(typeName: String, activeSocPrefixes: List<String>): String? {
        activeSocPrefixes.forEach { prefix ->
            if (typeName.startsWith(prefix)) {
                return SOC_PREFIXES[prefix] ?: prefix
            }
        }
        return null
    }

    private fun checkWrongRom(typeName: String, activeRomPrefixes: List<String>): String? {
        activeRomPrefixes.forEach { prefix ->
            if (typeName.startsWith(prefix)) {
                return ROM_PREFIXES[prefix] ?: prefix
            }
        }
        return null
    }

    private fun progress(
        current: Int,
        total: Int,
        message: String = "",
        file: String = ""
    ) = OperationProgress(
        current = current,
        total = total,
        currentFile = file,
        message = message
    )

    companion object {
        const val UNCHANGED_MARKER = "__UNCHANGED__"
    }
}

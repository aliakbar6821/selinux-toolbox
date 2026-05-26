package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.domain.path.PolicyPartition
import com.selinuxtoolbox.core.model.ActiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// ── Domain models ─────────────────────────────────────────────────────────────

data class DiffLine(
    val content: String,
    val sourceFile: String,
    val lineNumber: Int
)

data class DiffReport(
    val addedTypes: List<DiffLine>,
    val addedAttributes: List<DiffLine>,
    val addedAttributeSets: List<DiffLine>,
    val addedRules: List<DiffLine>,
    val addedContextEntries: List<DiffLine>,
    val generatedFileCount: Int,
    val baseTypeCount: Int
) {
    val totalAdditions: Int
        get() = addedTypes.size + addedAttributes.size +
                addedAttributeSets.size + addedRules.size + addedContextEntries.size

    val isEmpty: Boolean get() = totalAdditions == 0
}

sealed class DiffResult {
    data class Success(val report: DiffReport) : DiffResult()
    data class NoOutputs(val workPath: String) : DiffResult()
    data class SetupError(val reason: String) : DiffResult()
}

// ── Use case ──────────────────────────────────────────────────────────────────

@Singleton
class DiffPolicyUseCase @Inject constructor(
    private val pathResolver: PathResolver
) {
    // Regex patterns for CIL constructs
    private val reType      = Regex("""^\s*\(type\s+(\w+)\)""")
    private val reAttr      = Regex("""^\s*\(typeattribute\s+(\w+)\)""")
    private val reAttrSet   = Regex("""^\s*\(typeattributeset\s+\w+""")
    private val reAllow     = Regex("""^\s*\(allow\s+""")
    private val reNeverAllow= Regex("""^\s*\(neverallow\s+""")
    private val reAudit     = Regex("""^\s*\(auditallow\s+""")
    private val reDontAudit = Regex("""^\s*\(dontaudit\s+""")
    private val reTransition= Regex("""^\s*\(type(transition|change|member)\s+""")
    private val reContext   = Regex("""^\s*\(filecon\s+|^\s*\(genfscon\s+|^\s*\(portcon\s+""")

    suspend operator fun invoke(
        aospPath: String,
        workPath: String,
        mode: ActiveMode
    ): DiffResult = withContext(Dispatchers.IO) {

        val outputsDir = File(workPath, "outputs")
        if (!outputsDir.exists()) {
            return@withContext DiffResult.NoOutputs(workPath)
        }

        val generatedFiles = outputsDir.walkTopDown()
            .filter { it.isFile && it.extension == "cil" }
            .sortedBy { it.absolutePath }
            .toList()

        if (generatedFiles.isEmpty()) {
            return@withContext DiffResult.NoOutputs(workPath)
        }

        // Collect all type names from AOSP base CIL (what already exists)
        val baseTypes = mutableSetOf<String>()
        val baseAttrs = mutableSetOf<String>()
        PolicyPartition.values().forEach { partition ->
            pathResolver.allCilFiles(aospPath, partition, mode).forEach { file ->
                if (file.exists()) {
                    file.forEachLine { line ->
                        reType.find(line)?.groupValues?.getOrNull(1)?.let { baseTypes.add(it) }
                        reAttr.find(line)?.groupValues?.getOrNull(1)?.let { baseAttrs.add(it) }
                    }
                }
            }
        }

        // Parse generated files — collect only additions not in base
        val addedTypes       = mutableListOf<DiffLine>()
        val addedAttributes  = mutableListOf<DiffLine>()
        val addedAttrSets    = mutableListOf<DiffLine>()
        val addedRules       = mutableListOf<DiffLine>()
        val addedContexts    = mutableListOf<DiffLine>()

        generatedFiles.forEach { file ->
            val shortName = file.name
            file.readLines().forEachIndexed { idx, line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(";")) return@forEachIndexed

                val dl = DiffLine(trimmed, shortName, idx + 1)
                when {
                    reType.containsMatchIn(line) -> {
                        val name = reType.find(line)?.groupValues?.getOrNull(1) ?: ""
                        if (name !in baseTypes) addedTypes.add(dl)
                    }
                    reAttr.containsMatchIn(line) -> {
                        val name = reAttr.find(line)?.groupValues?.getOrNull(1) ?: ""
                        if (name !in baseAttrs) addedAttributes.add(dl)
                    }
                    reAttrSet.containsMatchIn(line)    -> addedAttrSets.add(dl)
                    reAllow.containsMatchIn(line)      -> addedRules.add(dl)
                    reNeverAllow.containsMatchIn(line) -> addedRules.add(dl)
                    reAudit.containsMatchIn(line)      -> addedRules.add(dl)
                    reDontAudit.containsMatchIn(line)  -> addedRules.add(dl)
                    reTransition.containsMatchIn(line) -> addedRules.add(dl)
                    reContext.containsMatchIn(line)    -> addedContexts.add(dl)
                }
            }
        }

        DiffResult.Success(
            DiffReport(
                addedTypes         = addedTypes,
                addedAttributes    = addedAttributes,
                addedAttributeSets = addedAttrSets,
                addedRules         = addedRules,
                addedContextEntries= addedContexts,
                generatedFileCount = generatedFiles.size,
                baseTypeCount      = baseTypes.size
            )
        )
    }
}

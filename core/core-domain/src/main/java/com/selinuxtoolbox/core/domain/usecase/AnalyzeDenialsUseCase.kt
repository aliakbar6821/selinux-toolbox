package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.analyzer.SafetyConfig
import com.selinuxtoolbox.core.domain.analyzer.CilGenerator
import com.selinuxtoolbox.core.domain.parser.AvcDenialParser
import com.selinuxtoolbox.core.domain.parser.DenialMetadataParser
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.model.ActiveMode
import com.selinuxtoolbox.core.model.AvcDenial
import com.selinuxtoolbox.core.model.Confidence
import com.selinuxtoolbox.core.model.DenialGroup
import com.selinuxtoolbox.core.model.DenialMetadataEntry
import com.selinuxtoolbox.core.model.DenialSource
import com.selinuxtoolbox.core.model.FixSuggestion
import com.selinuxtoolbox.core.domain.analyzer.TypeSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DenialAnalysisResult(
    val groups: List<DenialGroup>,
    val totalDenials: Int,
    val safeCount: Int,
    val reviewCount: Int,
    val unsafeCount: Int,
    val intentionalCount: Int
)

class AnalyzeDenialsUseCase @Inject constructor(
    private val avcParser: AvcDenialParser,
    private val metadataParser: DenialMetadataParser,
    private val pathResolver: PathResolver
) {
    suspend operator fun invoke(
        logFilePath: String,
        aospPath: String,
        mode: ActiveMode
    ): DenialAnalysisResult = withContext(Dispatchers.IO) {

        // 1. Parse all AVC denials from the log file
        val lines = File(logFilePath).readLines()
        val denials = avcParser.parseLines(lines, DenialSource.IMPORTED_FILE)

        // 2. Load selinux_denial_metadata from AOSP vendor (intentional denials)
        val metadataEntries = loadDenialMetadata(aospPath, mode)

        // 3. Group by (sourceDomain, targetType, objectClass)
        val grouped = avcParser.groupDenials(denials)

        // 4. Build DenialGroup with fix suggestion for each group
        val denialGroups = grouped.map { (_, groupDenials) ->
            val first = groupDenials.first()
            val permissions = groupDenials.map { it.permission }.distinct().sorted()

            // Check if intentional
            val intentionalEntry = metadataEntries.firstOrNull { entry ->
                metadataParser.isDenialExpected(
                    first.sourceDomain, first.permission,
                    first.targetType, first.objectClass,
                    metadataEntries
                ) != null
            }
            val isIntentional = intentionalEntry != null

            val suggestions = buildSuggestions(
                sourceDomain  = first.sourceDomain,
                targetType    = first.targetType,
                objectClass   = first.objectClass,
                permissions   = permissions,
                denialIds     = groupDenials.map { it.id },
                intentionalEntry = intentionalEntry
            )

            DenialGroup(
                sourceDomain = first.sourceDomain,
                targetType   = first.targetType,
                objectClass  = first.objectClass,
                permissions  = permissions,
                denials      = groupDenials,
                suggestions  = suggestions,
                isIntentional = isIntentional
            )
        }.sortedWith(
            // Show SAFE groups first, then REVIEW, then UNSAFE, then intentional last
            compareBy { group ->
                when (SafetyConfig.classify(group.sourceDomain)) {
                    TypeSafety.SAFE    -> 0
                    TypeSafety.REVIEW  -> 1
                    TypeSafety.UNSAFE  -> 2
                }
            }
        )

        val safeCount       = denialGroups.count { SafetyConfig.classify(it.sourceDomain) == TypeSafety.SAFE && !it.isIntentional }
        val reviewCount     = denialGroups.count { SafetyConfig.classify(it.sourceDomain) == TypeSafety.REVIEW && !it.isIntentional }
        val unsafeCount     = denialGroups.count { SafetyConfig.classify(it.sourceDomain) == TypeSafety.UNSAFE }
        val intentionalCount = denialGroups.count { it.isIntentional }

        DenialAnalysisResult(
            groups           = denialGroups,
            totalDenials     = denials.size,
            safeCount        = safeCount,
            reviewCount      = reviewCount,
            unsafeCount      = unsafeCount,
            intentionalCount = intentionalCount
        )
    }

    private fun buildSuggestions(
        sourceDomain: String,
        targetType: String,
        objectClass: String,
        permissions: List<String>,
        denialIds: List<String>,
        intentionalEntry: DenialMetadataEntry?
    ): List<FixSuggestion> {
        if (intentionalEntry != null) {
            return listOf(
                FixSuggestion.IntentionalDenial(
                    metadataReason   = intentionalEntry.reason
                        ?: "Listed in selinux_denial_metadata",
                    recommendation   = "This denial is intentional. Do not add an allow rule. " +
                        "If the service truly needs this access, investigate the OEM policy for the correct approach.",
                    relatedDenials   = denialIds
                )
            )
        }

        val safety = SafetyConfig.classify(sourceDomain)

        if (safety == TypeSafety.UNSAFE) {
            return listOf(
                FixSuggestion.NeverAllowConflict(
                    conflictingNeverAllow = "Source domain '$sourceDomain' is a critical system type",
                    explanation           = "Auto-generating allow rules for critical system domains " +
                        "(init, kernel, system_server, zygote etc.) is blocked. " +
                        "These denials require manual investigation.",
                    alternativeApproach   = "Check the OEM vendor_sepolicy.cil for how the original " +
                        "ROM handled this access, and port the specific rule manually.",
                    relatedDenials        = denialIds
                )
            )
        }

        val cilRule = CilGenerator.allowRule(sourceDomain, targetType, objectClass, permissions)
        val confidence = when (safety) {
            TypeSafety.SAFE   -> Confidence.HIGH
            TypeSafety.REVIEW -> Confidence.MEDIUM
            else              -> Confidence.LOW
        }

        return listOf(
            FixSuggestion.SimpleAllow(
                cilRule          = cilRule,
                targetFile       = "vendor_sepolicy.cil",
                targetPartition  = com.selinuxtoolbox.core.model.Partition.VENDOR,
                confidence       = confidence,
                explanation      = buildExplanation(sourceDomain, targetType, objectClass, permissions, safety),
                relatedDenials   = denialIds
            )
        )
    }

    private fun buildExplanation(
        source: String, target: String, cls: String,
        perms: List<String>, safety: TypeSafety
    ): String = buildString {
        append("Allow '$source' to access '$target' ($cls: ${perms.joinToString(", ")}). ")
        when (safety) {
            TypeSafety.SAFE   -> append("Source domain has a known-safe OEM prefix — safe to auto-apply.")
            TypeSafety.REVIEW -> append("Source domain is not in the known-safe list — review before applying.")
            TypeSafety.UNSAFE -> append("BLOCKED: critical system domain.")
        }
    }

    private fun loadDenialMetadata(aospPath: String, mode: ActiveMode): List<DenialMetadataEntry> {
        return try {
            val file = pathResolver.denialMetadataFile(aospPath, mode)
            if (file.exists()) metadataParser.parse(file.readText()) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

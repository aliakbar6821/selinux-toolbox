package com.selinuxtoolbox.core.model

import kotlinx.serialization.Serializable

// A parsed AVC denial from dmesg or logcat
@Serializable
data class AvcDenial(
    val id: String,                   // unique id for this denial
    val sourceDomain: String,         // scontext type e.g. mediaserver
    val targetType: String,           // tcontext type e.g. vendor_audio_device
    val objectClass: String,          // tclass e.g. chr_file
    val permission: String,           // denied permission e.g. read
    val comm: String?,                // process name
    val path: String?,                // file path if available
    val name: String?,                // object name
    val pid: Int?,
    val timestamp: Long?,
    val rawLine: String,
    val source: DenialSource
)

enum class DenialSource {
    LIVE_DMESG,      // captured from live dmesg stream
    IMPORTED_FILE,   // imported from a file by user
    LOGCAT           // from logcat
}

// Confidence level for a suggested fix
enum class Confidence {
    HIGH,    // Safe to apply, well understood
    MEDIUM,  // Likely correct but review recommended
    LOW      // Uncertain — manual review required
}

// A suggested fix for one or more denials
@Serializable
sealed class FixSuggestion {

    @Serializable
    data class SimpleAllow(
        val cilRule: String,              // the actual CIL rule to add
        val targetFile: String,           // which .cil file to add it to
        val targetPartition: Partition,
        val confidence: Confidence,
        val explanation: String,
        val relatedDenials: List<String>  // denial IDs this fixes
    ) : FixSuggestion()

    @Serializable
    data class AttributeAssignment(
        val cilRule: String,              // typeattributeset rule
        val explanation: String,
        val confidence: Confidence,
        val relatedDenials: List<String>
    ) : FixSuggestion()

    @Serializable
    data class NeverAllowConflict(
        val conflictingNeverAllow: String,
        val explanation: String,
        val alternativeApproach: String,
        val relatedDenials: List<String>
    ) : FixSuggestion()

    @Serializable
    data class IntentionalDenial(
        val metadataReason: String,
        val recommendation: String,
        val relatedDenials: List<String>
    ) : FixSuggestion()
}

// Grouped denials — multiple denials for the same source+target+class
@Serializable
data class DenialGroup(
    val sourceDomain: String,
    val targetType: String,
    val objectClass: String,
    val permissions: List<String>,    // all unique permissions denied
    val denials: List<AvcDenial>,
    val suggestions: List<FixSuggestion>,
    val isIntentional: Boolean        // found in selinux_denial_metadata
)

// Entry from selinux_denial_metadata
@Serializable
data class DenialMetadataEntry(
    val sourceDomain: String,
    val permission: String,
    val targetType: String,
    val objectClass: String,
    val reason: String?,
    val rawLine: String
)

package com.selinuxtoolbox.core.model

import kotlinx.serialization.Serializable

// The full loaded policy from all partitions
@Serializable
data class LoadedPolicy(
    val cilFiles: List<CilFile>,
    val contextEntries: List<ContextEntry>,
    val denialMetadata: List<DenialMetadataEntry>,
    val loadedAt: Long,
    val deviceInfo: DeviceInfo
)

// Basic device information
@Serializable
data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val vendorApiLevel: Int,           // from ro.vndk.version
    val selinuxMode: SelinuxMode,
    val availableMappingVersions: List<String>  // e.g. ["33.0", "34.0"]
)

// Current SELinux enforcement mode
enum class SelinuxMode {
    ENFORCING,
    PERMISSIVE,
    DISABLED,
    UNKNOWN
}

// A rule conflict found between two files
@Serializable
data class RuleConflict(
    val type: ConflictType,
    val statement1: CilStatement,
    val statement2: CilStatement,
    val description: String,
    val severity: ConflictSeverity
)

enum class ConflictType {
    DUPLICATE_TYPE,          // same type declared in two files
    DUPLICATE_RULE,          // identical allow rule in two files
    CONTRADICTING_RULES,     // allow in one file, neverallow in another
    MAPPING_VERSION_MISMATCH,// vendor API level vs available mapping files
    MISSING_PLATFORM_TYPE    // vendor uses type not in plat or mapping
}

enum class ConflictSeverity {
    CRITICAL,   // will cause boot failure or security hole
    HIGH,       // will cause runtime failures
    MEDIUM,     // may cause subtle issues
    LOW,        // cosmetic / dead code
    INFO        // informational only
}

// Result of seclabel validation
@Serializable
data class SeclabelValidation(
    val rcFilePath: String,
    val seclabel: String,
    val lineNumber: Int,
    val status: SeclabelStatus,
    val suggestion: String?
)

enum class SeclabelStatus {
    VALID,       // label exists in loaded CIL policy
    MISSING,     // label not found anywhere in policy
    SUSPICIOUS   // label exists but in wrong partition or unusual context
}

// A diff entry between live policy and project folder policy
@Serializable
data class PolicyDiffEntry(
    val type: DiffType,
    val element: String,          // type name, rule, or context pattern
    val liveValue: String?,       // what the live device has
    val projectValue: String?,    // what the project folder has
    val affectedFile: String,
    val severity: ConflictSeverity
)

enum class DiffType {
    MISSING_IN_LIVE,      // project has it, live device doesn't
    MISSING_IN_PROJECT,   // live has it, project doesn't
    VALUE_DIFFERS,        // both have it but content differs
    INVALID_SECLABEL,     // .rc file seclabel not in policy
    CORRECT_LABEL_FOUND   // app found the right label in project files
}

// Output from the compile/flash prep step
@Serializable
data class CompileResult(
    val success: Boolean,
    val errors: List<CompileError>,
    val warnings: List<String>,
    val outputPath: String?,           // path to precompiled_sepolicy
    val hashFiles: List<HashFile>,
    val compiledAt: Long
)

@Serializable
data class CompileError(
    val file: String,
    val line: Int,
    val message: String
)

@Serializable
data class HashFile(
    val name: String,                  // e.g. precompiled_sepolicy.plat_sepolicy_and_target_mapping.sha256
    val hash: String,
    val outputPath: String
)

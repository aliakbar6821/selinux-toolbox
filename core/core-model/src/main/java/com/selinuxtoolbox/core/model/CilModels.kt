package com.selinuxtoolbox.core.model

import kotlinx.serialization.Serializable

// Which partition a CIL file came from
enum class Partition {
    SYSTEM,
    SYSTEM_EXT,
    PRODUCT,
    VENDOR,
    ODM
}

// Classification of a CIL rule after analysis
enum class RuleClassification {
    ACTIVE,        // Used by running process or actual file on device
    ORPHANED,      // Type declared but never used anywhere
    WRONG_SOC,     // Contains SoC prefix from different chipset (e.g. qcom_ on MTK)
    WRONG_ROM,     // Contains OEM prefix from donor ROM (e.g. oneplus_ on HyperOS)
    PROTECTED,     // Referenced by mapping/ files — NEVER remove
    REVIEW,        // Cannot determine — keep but flag for manual review
    UNKNOWN        // Not yet analyzed
}

// A single parsed CIL statement
@Serializable
sealed class CilStatement {

    abstract val sourceFile: String
    abstract val lineNumber: Int
    abstract val partition: Partition

    @Serializable
    data class TypeDeclaration(
        val name: String,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class TypeAttribute(
        val name: String,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class TypeAttributeSet(
        val attribute: String,
        val members: List<String>,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class AllowRule(
        val source: String,
        val target: String,
        val objectClass: String,
        val permissions: List<String>,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class NeverAllowRule(
        val source: String,
        val target: String,
        val objectClass: String,
        val permissions: List<String>,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class AuditAllowRule(
        val source: String,
        val target: String,
        val objectClass: String,
        val permissions: List<String>,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class DontAuditRule(
        val source: String,
        val target: String,
        val objectClass: String,
        val permissions: List<String>,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class TypeTransition(
        val source: String,
        val target: String,
        val objectClass: String,
        val defaultType: String,
        val objectName: String?,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class TypeChange(
        val source: String,
        val target: String,
        val objectClass: String,
        val defaultType: String,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class RoleAllow(
        val source: String,
        val target: String,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class GenFsCon(
        val fsType: String,
        val path: String,
        val context: String,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()

    @Serializable
    data class GenericStatement(
        val keyword: String,
        val rawContent: String,
        override val sourceFile: String,
        override val lineNumber: Int,
        override val partition: Partition
    ) : CilStatement()
}

// A CIL file loaded from a partition
@Serializable
data class CilFile(
    val partition: Partition,
    val absolutePath: String,
    val relativePath: String,  // e.g. "etc/selinux/vendor_sepolicy.cil"
    val isMapping: Boolean,    // true if inside mapping/ directory — NEVER modify
    val sha256: String,
    val statements: List<CilStatement> = emptyList()
)

// Represents a type with all its metadata after full policy analysis
@Serializable
data class PolicyType(
    val name: String,
    val declaredIn: String,           // file path
    val partition: Partition,
    val attributes: List<String>,     // which attributes this type belongs to
    val allowRules: List<CilStatement.AllowRule>,
    val isProtected: Boolean,         // referenced by mapping files
    val classification: RuleClassification
)

// Result of analyzing a single rule during cleanup
@Serializable
data class RuleAnalysisResult(
    val statement: CilStatement,
    val classification: RuleClassification,
    val reason: String,               // human-readable explanation
    val suggestedAction: SuggestedAction
)

enum class SuggestedAction {
    KEEP,
    REMOVE,
    COMMENT_OUT,   // keep but add "; REVIEW:" prefix
    MANUAL_REVIEW
}

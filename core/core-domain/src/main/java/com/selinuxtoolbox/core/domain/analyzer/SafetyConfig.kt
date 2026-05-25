package com.selinuxtoolbox.core.domain.analyzer

// Classification of a type name before any CIL is generated.
// UNSAFE  → block entirely, never auto-generate
// SAFE    → auto-generate with single bulk confirm
// REVIEW  → show to user, require per-item confirmation
enum class TypeSafety {
    UNSAFE,
    SAFE,
    REVIEW
}

object SafetyConfig {

    // ── UNSAFE: core system types — never touch ───────────────────────────────
    private val UNSAFE_EXACT = setOf(
        "init", "kernel", "ueventd", "system_server", "zygote",
        "servicemanager", "hwservicemanager", "vndservicemanager",
        "vold", "netd", "installd", "logd", "healthd", "lmkd",
        "storaged", "apexd", "linkerconfig", "su", "shell", "adbd",
        "magisk", "rootfs", "tmpfs", "proc", "sysfs", "selinuxfs",
        "cgroup", "dumpstate", "debuggerd", "perfetto"
    )

    // ── SAFE prefixes: OEM/vendor specific types ──────────────────────────────
    private val SAFE_PREFIXES = listOf(
        "oplus_", "oppo_", "oneplus_", "realme_", "coloros_",
        "vendor_oplus_", "vendor_oppo_",
        "qti_", "vendor_qti_", "qcom_", "mtk_", "vendor_mtk_",
        "mediatek_", "vendor_", "odm_", "hal_"
    )

    // ── SAFE suffixes: well-understood type categories ────────────────────────
    private val SAFE_SUFFIXES = listOf(
        "_exec", "_file", "_data_file", "_config_file", "_prop",
        "_service", "_hwservice", "_device", "_socket", "_tmpfs"
    )

    // ── Versioned alias filter — never declare typeattribute for these ────────
    // ^base_typeattr_\d   → compiler internal
    // _\d+_\d+$           → API version suffix e.g. _34_0
    // _20\d{4}$           → QPR date suffix e.g. _202404
    private val VERSIONED_ALIAS_PATTERNS = listOf(
        Regex("""^base_typeattr_\d"""),
        Regex("""_\d+_\d+$"""),
        Regex("""_20\d{4}$""")
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun classify(typeName: String): TypeSafety {
        // Exact unsafe match
        if (typeName in UNSAFE_EXACT) return TypeSafety.UNSAFE

        // Prefix-based safe match
        if (SAFE_PREFIXES.any { typeName.startsWith(it) }) return TypeSafety.SAFE

        // Suffix-based safe match
        if (SAFE_SUFFIXES.any { typeName.endsWith(it) }) return TypeSafety.SAFE

        // Everything else needs user review
        return TypeSafety.REVIEW
    }

    // Returns true if this attribute name is a versioned compiler alias
    // that should NEVER be declared as a (typeattribute) in generated CIL
    fun isVersionedAlias(attrName: String): Boolean =
        VERSIONED_ALIAS_PATTERNS.any { it.containsMatchIn(attrName) }

    // Convenience: is this type safe to auto-generate without user confirmation?
    fun isAutoSafe(typeName: String): Boolean = classify(typeName) == TypeSafety.SAFE

    // Convenience: should this type be blocked completely?
    fun isUnsafe(typeName: String): Boolean = classify(typeName) == TypeSafety.UNSAFE
}

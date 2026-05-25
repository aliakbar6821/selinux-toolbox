package com.selinuxtoolbox.core.domain.analyzer

import com.selinuxtoolbox.core.domain.path.PolicyPartition

// Exact CIL output format from the project brief.
// Every function returns a multi-line String ready to append to a .cil file.
object CilGenerator {

    // ── Domain type (service that runs as a process) ──────────────────────────
    fun domainType(name: String): String = buildString {
        appendLine("(type $name)")
        appendLine("(roletype object_r $name)")
        appendLine("(typeattributeset domain ($name))")
        appendLine("(allow $name $name (process (fork sigchld sigkill sigstop")
        appendLine("  signull signal getsched setsched getpgid setpgid getcap setcap")
        appendLine("  share getattr setrlimit)))")
        appendLine("(allow $name $name (fd (use)))")
        appendLine("(allow $name $name (fifo_file (ioctl read write getattr lock append open)))")
    }

    // ── Exec type (binary that transitions into a domain) ─────────────────────
    fun execType(name: String, partition: PolicyPartition): String = buildString {
        val execName = "${name}_exec"
        val partAttr = partitionFileTypeAttr(partition)
        appendLine("(type $execName)")
        appendLine("(roletype object_r $execName)")
        appendLine("(typeattributeset file_type ($execName))")
        appendLine("(typeattributeset exec_type ($execName))")
        appendLine("(typeattributeset $partAttr ($execName))")
    }

    // ── Domain transition (init spawns the service) ───────────────────────────
    fun domainTransition(name: String): String = buildString {
        val execName = "${name}_exec"
        appendLine("(typetransition init $execName process $name)")
        appendLine("(allow init $execName (file (read getattr map execute open)))")
        appendLine("(allow init $name (process (transition)))")
        appendLine("(allow $name $execName (file (entrypoint read execute getattr map open)))")
    }

    // ── File type ─────────────────────────────────────────────────────────────
    fun fileType(name: String, partition: PolicyPartition): String = buildString {
        val partAttr = partitionFileTypeAttr(partition)
        appendLine("(type $name)")
        appendLine("(roletype object_r $name)")
        appendLine("(typeattributeset file_type ($name))")
        appendLine("(typeattributeset $partAttr ($name))")
    }

    // ── Device type ───────────────────────────────────────────────────────────
    fun deviceType(name: String): String = buildString {
        appendLine("(type $name)")
        appendLine("(roletype object_r $name)")
        appendLine("(typeattributeset dev_type ($name))")
        appendLine("(typeattributeset file_type ($name))")
    }

    // ── Property type ─────────────────────────────────────────────────────────
    fun propertyType(name: String): String = buildString {
        appendLine("(type $name)")
        appendLine("(roletype object_r $name)")
        appendLine("(typeattributeset property_type ($name))")
    }

    // ── Service type ──────────────────────────────────────────────────────────
    fun serviceType(name: String): String = buildString {
        appendLine("(type $name)")
        appendLine("(roletype object_r $name)")
        appendLine("(typeattributeset service_manager_type ($name))")
    }

    // ── Hwservice type ────────────────────────────────────────────────────────
    fun hwserviceType(name: String): String = buildString {
        appendLine("(type $name)")
        appendLine("(roletype object_r $name)")
        appendLine("(typeattributeset hwservice_manager_type ($name))")
    }

    // ── Typeattribute declaration (for missing attributes) ────────────────────
    fun typeAttribute(name: String): String = "(typeattribute $name)\n"

    // ── Allow rule ────────────────────────────────────────────────────────────
    fun allowRule(
        source: String,
        target: String,
        objectClass: String,
        permissions: List<String>
    ): String {
        val permsStr = permissions.distinct().sorted().joinToString(" ")
        return "(allow $source $target ($objectClass ($permsStr)))\n"
    }

    // ── File context entry ────────────────────────────────────────────────────
    // e.g.  /system_ext/bin/myservice    u:object_r:myservice_exec:s0
    fun fileContextEntry(path: String, typeName: String, isExec: Boolean = false): String {
        val contextType = if (isExec) "${typeName}_exec" else typeName
        return "$path    u:object_r:$contextType:s0\n"
    }

    // ── Full domain block (domain + exec + transition) ────────────────────────
    // This is the combined output for a single missing seclabel
    fun fullDomainBlock(
        domainName: String,
        execPath: String,
        partition: PolicyPartition
    ): String = buildString {
        appendLine("; ── $domainName ──────────────────────────────────────────")
        appendLine("; Source: $execPath")
        appendLine()
        append(domainType(domainName))
        appendLine()
        append(execType(domainName, partition))
        appendLine()
        append(domainTransition(domainName))
        appendLine()
    }

    // ── INSTRUCTIONS.txt content ──────────────────────────────────────────────
    fun instructionsTxt(
        operation: String,
        outputFiles: List<OutputFileInstruction>
    ): String = buildString {
        appendLine("=" .repeat(70))
        appendLine("SELinux Toolbox — Output Instructions")
        appendLine("Operation: $operation")
        appendLine("Generated: ${java.util.Date()}")
        appendLine("=" .repeat(70))
        appendLine()
        appendLine("⚠ BACKUP YOUR DEVICE BEFORE FLASHING ANYTHING.")
        appendLine("⚠ ALWAYS HAVE A BOOTLOOP RECOVERY PLAN (TWRP/fastboot).")
        appendLine()
        appendLine("Files in this folder:")
        appendLine()
        outputFiles.forEach { f ->
            appendLine("  ${f.fileName}")
            appendLine("    → Destination: ${f.destination}")
            appendLine("    → Action:      ${f.action}")
            if (f.notes.isNotEmpty()) {
                f.notes.forEach { note -> appendLine("    → Note: $note") }
            }
            appendLine()
        }
        appendLine("=" .repeat(70))
        appendLine("Load order note:")
        appendLine("  If you generated all_missing_attributes.cil, it MUST be")
        appendLine("  loaded BEFORE vendor_sepolicy.cil in the secilc command.")
        appendLine("=" .repeat(70))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun partitionFileTypeAttr(partition: PolicyPartition): String =
        when (partition) {
            PolicyPartition.SYSTEM     -> "system_file_type"
            PolicyPartition.SYSTEM_EXT -> "system_ext_file_type"
            PolicyPartition.PRODUCT    -> "product_file_type"
            PolicyPartition.VENDOR     -> "vendor_file_type"
            PolicyPartition.ODM        -> "vendor_file_type" // ODM uses vendor attr
        }
}

// Describes one output file for INSTRUCTIONS.txt
data class OutputFileInstruction(
    val fileName: String,
    val destination: String,    // where it goes in the port
    val action: String,         // "APPEND to existing file" or "NEW FILE"
    val notes: List<String> = emptyList()
)

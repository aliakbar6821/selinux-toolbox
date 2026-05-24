package com.selinuxtoolbox.core.model

import kotlinx.serialization.Serializable

// Types of context files
enum class ContextFileType {
    FILE,         // file_contexts
    SERVICE,      // service_contexts
    PROPERTY,     // property_contexts
    HWSERVICE,    // hwservice_contexts
    VNDSERVICE,   // vndservice_contexts
    SEAPP,        // seapp_contexts
    PROCESS       // live process contexts from ps -eZ
}

// A single entry in any *_contexts file
@Serializable
data class ContextEntry(
    val pattern: String,          // path pattern or service name
    val context: String,          // full SELinux context u:object_r:type:s0
    val type: String,             // extracted type from context
    val fileType: ContextFileType,
    val sourceFile: String,
    val lineNumber: Int,
    val partition: Partition
)

// Parsed SELinux security context
@Serializable
data class SelinuxContext(
    val user: String,             // u
    val role: String,             // r or object_r
    val type: String,             // the actual type e.g. vendor_sepolicy_file
    val sensitivity: String,      // s0
    val raw: String               // full string u:object_r:vendor_sepolicy_file:s0
) {
    companion object {
        val UNKNOWN = SelinuxContext("?", "?", "?", "?", "?:?:?:?")

        fun parse(raw: String): SelinuxContext? {
            val parts = raw.trim().split(":")
            if (parts.size < 4) return null
            return SelinuxContext(
                user = parts[0],
                role = parts[1],
                type = parts[2],
                sensitivity = parts.drop(3).joinToString(":"),
                raw = raw
            )
        }
    }
}

// A live process with its SELinux context
@Serializable
data class ProcessContext(
    val pid: Int,
    val name: String,
    val context: SelinuxContext,
    val uid: String
)

// A file with its SELinux context from ls -Z
@Serializable
data class FileContext(
    val path: String,
    val context: SelinuxContext,
    val isDirectory: Boolean,
    val permissions: String      // e.g. -rwxr-xr-x
)

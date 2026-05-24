package com.selinuxtoolbox.core.model

import kotlinx.serialization.Serializable

// Overall project status
enum class ProjectStatus {
    ACTIVE,
    ARCHIVED,
    RESTORED
}

// Type of action performed
enum class ActionType {
    CLEANUP_PASS,
    DENIAL_RULE_ADD,
    SECLABEL_FIX,
    CONTEXT_UPDATE,
    CONFLICT_RESOLVE,
    COMPILE,
    MANUAL_EDIT,
    RESTORE
}

// Whether a previous action is still valid after restore
enum class ActionValidity {
    ALREADY_APPLIED,       // files already match the modified state
    NEEDS_REAPPLY,         // files match original — safe to re-apply
    PARTIALLY_APPLICABLE,  // files changed — user must review
    NOT_APPLICABLE         // target files no longer exist
}

// File operation type for snapshots
enum class FileOperation {
    MODIFIED,
    CREATED,
    DELETED
}

// A snapshot of one file before an action was applied
@Serializable
data class FileSnapshot(
    val filePath: String,
    val originalHash: String,
    val modifiedHash: String?,
    val operation: FileOperation
)

// Full description of one action and its undo data
@Serializable
data class ActionRecord(
    val id: Long,
    val projectId: Long,
    val type: ActionType,
    val description: String,
    val timestamp: Long,
    val backupZipPath: String,        // path to zip of files before this action
    val changedFiles: List<FileSnapshot>,
    val undone: Boolean = false,
    val undoneAt: Long? = null,
    val metadata: Map<String, String> = emptyMap()  // action-specific extra data
)

// Validation of a previous action after project restore
@Serializable
data class ActionValidation(
    val action: ActionRecord,
    val validity: ActionValidity,
    val explanation: String
)

// A note attached to a project or specific action
@Serializable
data class ProjectNote(
    val id: Long,
    val projectId: Long,
    val actionId: Long?,          // null = project-level note
    val content: String,
    val timestamp: Long,
    val tags: List<String>
)

// Full project model
@Serializable
data class Project(
    val id: Long,
    val name: String,
    val sourceDevice: String,     // e.g. "OnePlus Nord 3"
    val targetDevice: String,     // e.g. "Moto G54"
    val sourceRom: String,        // e.g. "OxygenOS 13.1"
    val targetRom: String,        // e.g. "HyperOS"
    val projectFolderPath: String,
    val createdAt: Long,
    val lastModified: Long,
    val status: ProjectStatus,
    val actions: List<ActionRecord> = emptyList(),
    val notes: List<ProjectNote> = emptyList()
)

package com.selinuxtoolbox.core.model

import kotlinx.serialization.Serializable

enum class ProjectStatus { ACTIVE, ARCHIVED, RESTORED }

enum class ActionType {
    CLEANUP_PASS, DENIAL_RULE_ADD, SECLABEL_FIX, CONTEXT_UPDATE,
    CONFLICT_RESOLVE, COMPILE, MANUAL_EDIT, RESTORE
}

enum class ActionValidity {
    ALREADY_APPLIED, NEEDS_REAPPLY, PARTIALLY_APPLICABLE, NOT_APPLICABLE
}

enum class FileOperation { MODIFIED, CREATED, DELETED }

enum class LogType { DMESG, LOGCAT, LAST_KMSG, UNKNOWN }

@Serializable
data class FileSnapshot(
    val filePath: String,
    val originalHash: String,
    val modifiedHash: String?,
    val operation: FileOperation
)

@Serializable
data class ActionRecord(
    val id: Long,
    val projectId: Long,
    val type: ActionType,
    val description: String,
    val timestamp: Long,
    val backupZipPath: String,
    val changedFiles: List<FileSnapshot>,
    val undone: Boolean = false,
    val undoneAt: Long? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ActionValidation(
    val action: ActionRecord,
    val validity: ActionValidity,
    val explanation: String
)

@Serializable
data class ProjectNote(
    val id: Long,
    val projectId: Long,
    val actionId: Long?,
    val content: String,
    val timestamp: Long,
    val tags: List<String>
)

@Serializable
data class ImportedLog(
    val id: Long,
    val projectId: Long,
    val fileName: String,
    val filePath: String,
    val importedAt: Long,
    val logType: LogType,
    val totalLines: Int,
    val avcDenialCount: Int,
    val unmappedContextCount: Int,
    val undefinedTypeCount: Int
)

@Serializable
data class Project(
    val id: Long,
    val name: String,
    val sourceDevice: String,
    val targetDevice: String,
    val sourceRom: String,
    val targetRom: String,
    val projectFolderPath: String,
    val createdAt: Long,
    val lastModified: Long,
    val status: ProjectStatus,
    val oemPath: String = "",
    val aospPath: String = "",
    val workPath: String = "",
    val mappingVersion: String = "34.0",
    val activeMode: ActiveMode = ActiveMode.OFFLINE
)

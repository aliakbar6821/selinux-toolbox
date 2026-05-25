package com.selinuxtoolbox.core.model

import kotlinx.serialization.Serializable

// Operating mode — determines where files are read from
enum class ActiveMode {
    OFFLINE,  // Read from OEM/ and AOSP/ folders on SD card
    LIVE      // Read from live mounted partitions via root
}

// Health status of a workspace folder
enum class FolderHealth {
    OK,           // folder exists and has expected selinux files
    MISSING,      // folder does not exist
    EMPTY,        // folder exists but has no .cil or context files
    PARTIAL,      // some expected files present, some missing
    NO_PERMISSION // folder exists but cannot be read
}

// Result of checking one partition subfolder
@Serializable
data class PartitionHealth(
    val partition: String,
    val selinuxDirExists: Boolean,
    val cilFilesFound: Int,
    val contextFilesFound: Int,
    val initDirExists: Boolean,
    val rcFilesFound: Int,
    val health: FolderHealth,
    val notes: List<String>
)

// Full workspace validation result
@Serializable
data class WorkspaceValidation(
    val oemPath: String,
    val aospPath: String,
    val workPath: String,
    val oemPartitions: List<PartitionHealth>,
    val aospPartitions: List<PartitionHealth>,
    val mappingVersion: String,
    val isValid: Boolean,
    val warnings: List<String>,
    val errors: List<String>
)

// Workspace project — NEW project model with OEM/AOSP paths
@Serializable
data class WorkspaceProject(
    val id: String,
    val name: String,
    val oemDevice: String,
    val oemRom: String,
    val aospDevice: String,
    val aospRom: String,
    val workspacePath: String,
    val oemPath: String,
    val aospPath: String,
    val workPath: String,
    val mappingVersion: String,
    val createdAt: Long,
    val lastModified: Long,
    val status: ProjectStatus,
    val activeMode: ActiveMode
)

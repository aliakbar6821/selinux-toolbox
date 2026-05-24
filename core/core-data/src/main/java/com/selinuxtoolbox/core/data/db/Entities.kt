package com.selinuxtoolbox.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.selinuxtoolbox.core.model.ActionType
import com.selinuxtoolbox.core.model.FileOperation
import com.selinuxtoolbox.core.model.ProjectStatus

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceDevice: String,
    val targetDevice: String,
    val sourceRom: String,
    val targetRom: String,
    val projectFolderPath: String,
    val createdAt: Long,
    val lastModified: Long,
    val status: ProjectStatus
)

@Entity(
    tableName = "actions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val type: ActionType,
    val description: String,
    val timestamp: Long,
    val backupZipPath: String,
    val changedFilesJson: String,   // JSON serialized List<FileSnapshot>
    val undone: Boolean = false,
    val undoneAt: Long? = null,
    val metadataJson: String = "{}" // JSON serialized Map<String,String>
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val actionId: Long?,
    val content: String,
    val timestamp: Long,
    val tagsJson: String = "[]"     // JSON serialized List<String>
)

@Entity(
    tableName = "file_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ActionEntity::class,
            parentColumns = ["id"],
            childColumns = ["actionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("actionId")]
)
data class FileSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionId: Long,
    val filePath: String,
    val originalHash: String,
    val modifiedHash: String?,
    val operation: FileOperation
)

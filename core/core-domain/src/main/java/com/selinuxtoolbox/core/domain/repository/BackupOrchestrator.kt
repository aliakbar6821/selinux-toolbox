package com.selinuxtoolbox.core.domain.repository

import com.selinuxtoolbox.core.data.util.FileUtil
import com.selinuxtoolbox.core.model.ActionRecord
import com.selinuxtoolbox.core.model.ActionType
import com.selinuxtoolbox.core.model.FileOperation
import com.selinuxtoolbox.core.model.FileSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Result of a backup+apply operation
sealed class BackupResult {
    data class Success(
        val actionId: Long,
        val backupZipPath: String,
        val changedFiles: List<FileSnapshot>
    ) : BackupResult()

    data class Failure(
        val reason: String,
        val cause: Throwable? = null
    ) : BackupResult()
}

// Result of an undo operation
sealed class UndoResult {
    data class Success(val restoredFiles: List<String>) : UndoResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : UndoResult()
    data class ConflictWarning(
        val restoredFiles: List<String>,
        val conflictingActions: List<ActionRecord>
    ) : UndoResult()
}

@Singleton
class BackupOrchestrator @Inject constructor(
    private val actionRepository: ActionRepository
) {

    // -------------------------------------------------------------------------
    // Core workflow: snapshot → apply → log
    //
    // Every feature that modifies files calls this instead of writing directly.
    // Guarantees:
    //   1. Backup zip created BEFORE any file is touched
    //   2. Action recorded in Room with file hashes
    //   3. On failure: backup zip is left intact for manual recovery
    // -------------------------------------------------------------------------

    suspend fun executeWithBackup(
        projectId: Long,
        actionType: ActionType,
        description: String,
        projectOutputDir: String,     // e.g. /sdcard/SELinuxToolbox/projects/MyProject
        filesToModify: List<File>,    // files that WILL be changed
        metadata: Map<String, String> = emptyMap(),
        action: suspend () -> Map<File, String>  // returns Map<file, newContent>
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            // ------------------------------------------------------------------
            // 1. Snapshot original state of all files before touching anything
            // ------------------------------------------------------------------
            val snapshots = mutableListOf<FileSnapshot>()
            val originalContents = mutableMapOf<File, String?>()

            for (file in filesToModify) {
                val originalContent = if (file.exists()) file.readText() else null
                val originalHash = originalContent?.let { FileUtil.sha256(it) } ?: ""
                originalContents[file] = originalContent
                snapshots.add(
                    FileSnapshot(
                        filePath = file.absolutePath,
                        originalHash = originalHash,
                        modifiedHash = null,    // filled after apply
                        operation = when {
                            !file.exists() -> FileOperation.CREATED
                            else -> FileOperation.MODIFIED
                        }
                    )
                )
            }

            // ------------------------------------------------------------------
            // 2. Create backup zip of original files
            //    Zip goes to: projectOutputDir/action_backups/<timestamp>.zip
            // ------------------------------------------------------------------
            val backupDir = File(projectOutputDir, "action_backups")
            backupDir.mkdirs()
            val timestamp = System.currentTimeMillis()
            val backupZip = File(backupDir, "backup_${timestamp}.zip")

            val existingFiles = filesToModify.filter { it.exists() }
            if (existingFiles.isNotEmpty()) {
                val zipOk = FileUtil.createZip(
                    files = existingFiles,
                    outputZip = backupZip,
                    baseDir = ""
                )
                if (!zipOk) {
                    return@withContext BackupResult.Failure(
                        "Failed to create backup zip at ${backupZip.absolutePath}"
                    )
                }
            } else {
                // No existing files — create empty marker zip
                backupZip.createNewFile()
            }

            // ------------------------------------------------------------------
            // 3. Record action start in Room (before applying changes)
            // ------------------------------------------------------------------
            val actionId = actionRepository.recordActionStart(
                projectId = projectId,
                type = actionType,
                description = description,
                backupZipPath = backupZip.absolutePath,
                metadata = metadata
            )

            // ------------------------------------------------------------------
            // 4. Execute the actual modification
            // ------------------------------------------------------------------
            val modifiedContents: Map<File, String> = try {
                action()
            } catch (e: Exception) {
                // Action failed — backup zip is still intact
                // Mark action as failed in metadata but do not delete it
                actionRepository.recordActionComplete(actionId, snapshots)
                return@withContext BackupResult.Failure(
                    "Action execution failed: ${e.message}",
                    e
                )
            }

            // ------------------------------------------------------------------
            // 5. Write modified content to files
            // ------------------------------------------------------------------
            val finalSnapshots = mutableListOf<FileSnapshot>()
            for (snapshot in snapshots) {
                val file = File(snapshot.filePath)
                val newContent = modifiedContents[file]

                if (newContent != null) {
                    file.parentFile?.mkdirs()
                    file.writeText(newContent)
                    val modifiedHash = FileUtil.sha256(newContent)
                    finalSnapshots.add(snapshot.copy(modifiedHash = modifiedHash))
                } else {
                    // File was in the list but action did not produce output for it
                    finalSnapshots.add(snapshot)
                }
            }

            // ------------------------------------------------------------------
            // 6. Update Room record with final file hashes
            // ------------------------------------------------------------------
            actionRepository.recordActionComplete(actionId, finalSnapshots)

            BackupResult.Success(
                actionId = actionId,
                backupZipPath = backupZip.absolutePath,
                changedFiles = finalSnapshots
            )

        } catch (e: Exception) {
            BackupResult.Failure(
                "Unexpected error during backup+apply: ${e.message}",
                e
            )
        }
    }

    // -------------------------------------------------------------------------
    // Undo a single action
    //
    // Restores files from the action's backup zip.
    // Checks if later actions modified the same files (conflict warning).
    // Action is marked undone in Room but NOT deleted (audit trail preserved).
    // -------------------------------------------------------------------------

    suspend fun undoAction(
        actionId: Long,
        projectId: Long
    ): UndoResult = withContext(Dispatchers.IO) {
        try {
            val action = actionRepository.getActionById(actionId)
                ?: return@withContext UndoResult.Failure("Action $actionId not found")

            if (action.undone) {
                return@withContext UndoResult.Failure(
                    "Action ${action.id} is already undone"
                )
            }

            val backupZip = File(action.backupZipPath)
            if (!backupZip.exists()) {
                return@withContext UndoResult.Failure(
                    "Backup zip not found: ${action.backupZipPath}"
                )
            }

            // ------------------------------------------------------------------
            // Check for conflicts: later actions that touched the same files
            // ------------------------------------------------------------------
            val laterActions = actionRepository.getActionsForProjectOnce(projectId)
                .filter { it.timestamp > action.timestamp && !it.undone }

            val myFiles = action.changedFiles.map { it.filePath }.toSet()
            val conflictingActions = laterActions.filter { laterAction ->
                laterAction.changedFiles.any { it.filePath in myFiles }
            }

            // ------------------------------------------------------------------
            // Restore files from backup zip
            // ------------------------------------------------------------------
            val restoreDir = File(backupZip.parent, "undo_temp_${System.currentTimeMillis()}")
            val extracted = FileUtil.extractZip(backupZip, restoreDir)

            if (!extracted) {
                return@withContext UndoResult.Failure(
                    "Failed to extract backup zip: ${backupZip.absolutePath}"
                )
            }

            val restoredFiles = mutableListOf<String>()

            for (snapshot in action.changedFiles) {
                val targetFile = File(snapshot.filePath)
                when (snapshot.operation) {
                    FileOperation.CREATED -> {
                        // File was created by this action — delete it on undo
                        if (targetFile.exists()) {
                            targetFile.delete()
                            restoredFiles.add(snapshot.filePath)
                        }
                    }
                    FileOperation.MODIFIED -> {
                        // Restore from backup zip
                        // The zip entry name is the filename only (from FileUtil.createZip)
                        val backedUpFile = File(restoreDir, targetFile.name)
                        if (backedUpFile.exists()) {
                            targetFile.parentFile?.mkdirs()
                            backedUpFile.copyTo(targetFile, overwrite = true)
                            restoredFiles.add(snapshot.filePath)
                        }
                    }
                    FileOperation.DELETED -> {
                        // File was deleted by action — nothing to restore
                        // (original was deleted, backup has it, but it should
                        //  not exist in current state after undo either)
                    }
                }
            }

            // Clean up temp extraction dir
            restoreDir.deleteRecursively()

            // Mark action as undone in Room
            actionRepository.markActionUndone(actionId)

            // Return with conflict warning if later actions touched same files
            if (conflictingActions.isNotEmpty()) {
                UndoResult.ConflictWarning(
                    restoredFiles = restoredFiles,
                    conflictingActions = conflictingActions
                )
            } else {
                UndoResult.Success(restoredFiles)
            }

        } catch (e: Exception) {
            UndoResult.Failure(
                "Unexpected error during undo: ${e.message}",
                e
            )
        }
    }

    // -------------------------------------------------------------------------
    // Create a full project snapshot (used for project export / before
    // major operations like a full cleanup pass)
    // -------------------------------------------------------------------------

    suspend fun createFullSnapshot(
        sourceDir: File,
        outputZip: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val allFiles = sourceDir.walkTopDown()
                .filter { it.isFile }
                .toList()

            FileUtil.createZip(
                files = allFiles,
                outputZip = outputZip,
                baseDir = sourceDir.absolutePath
            )
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // Restore a full project snapshot (used after factory reset / reimport)
    // -------------------------------------------------------------------------

    suspend fun restoreFullSnapshot(
        snapshotZip: File,
        targetDir: File
    ): Boolean = withContext(Dispatchers.IO) {
        FileUtil.extractZip(snapshotZip, targetDir)
    }
}

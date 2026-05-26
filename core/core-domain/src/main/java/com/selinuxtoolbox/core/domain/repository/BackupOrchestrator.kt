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
    // Core workflow: snapshot → zip → record → apply → update hashes
    //
    // Every feature that modifies files calls this instead of writing directly.
    // Guarantees:
    //   1. Backup zip created BEFORE any file is touched
    //   2. Zip entries use relative paths (no filename collision possible)
    //   3. Action recorded in Room with file hashes before and after
    //   4. On failure: backup zip is left intact for manual recovery
    // -------------------------------------------------------------------------

    suspend fun executeWithBackup(
        projectId: Long,
        actionType: ActionType,
        description: String,
        projectOutputDir: String,
        filesToModify: List<File>,
        metadata: Map<String, String> = emptyMap(),
        action: suspend () -> Map<File, String>  // returns Map<file, newContent>
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            // ------------------------------------------------------------------
            // 1. Snapshot original state of all files
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
                        modifiedHash = null,
                        operation = if (!file.exists()) FileOperation.CREATED
                                    else FileOperation.MODIFIED
                    )
                )
            }

            // ------------------------------------------------------------------
            // 2. Create backup zip with relative paths preserved
            //
            // Use the common ancestor of all files as baseDir so that
            // zip entries look like:
            //   vendor/vendor_sepolicy.cil
            //   system/plat_sepolicy.cil
            // instead of just the filename — preventing collision on restore.
            // ------------------------------------------------------------------
            val backupDir = File(projectOutputDir, "action_backups")
            backupDir.mkdirs()
            val timestamp = System.currentTimeMillis()
            val backupZip = File(backupDir, "backup_${timestamp}.zip")

            val existingFiles = filesToModify.filter { it.exists() }

            // Compute baseDir: common ancestor of all files being backed up.
            // Falls back to empty string (filename-only) only if single file.
            val baseDir = if (existingFiles.size > 1) {
                FileUtil.commonAncestor(existingFiles)?.canonicalPath ?: ""
            } else {
                existingFiles.firstOrNull()?.parentFile?.canonicalPath ?: ""
            }

            if (existingFiles.isNotEmpty()) {
                val zipOk = FileUtil.createZip(
                    files = existingFiles,
                    outputZip = backupZip,
                    baseDir = baseDir
                )
                if (!zipOk) {
                    return@withContext BackupResult.Failure(
                        "Failed to create backup zip at ${backupZip.absolutePath}"
                    )
                }
            } else {
                backupZip.createNewFile()
            }

            // Store baseDir in metadata so undoAction knows how to restore
            val enrichedMetadata = metadata + mapOf(
                "backup_base_dir" to baseDir,
                "backup_zip_path" to backupZip.absolutePath
            )

            // ------------------------------------------------------------------
            // 3. Record action start in Room (BEFORE applying changes)
            // ------------------------------------------------------------------
            val actionId = actionRepository.recordActionStart(
                projectId = projectId,
                type = actionType,
                description = description,
                backupZipPath = backupZip.absolutePath,
                metadata = enrichedMetadata
            )

            // ------------------------------------------------------------------
            // 4. Execute the actual modification
            // ------------------------------------------------------------------
            val modifiedContents: Map<File, String> = try {
                action()
            } catch (e: Exception) {
                // Action failed — backup zip is still intact
                actionRepository.recordActionComplete(actionId, snapshots)
                return@withContext BackupResult.Failure(
                    "Action execution failed: ${e.message}", e
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
                "Unexpected error during backup+apply: ${e.message}", e
            )
        }
    }

    // -------------------------------------------------------------------------
    // Undo a single action
    //
    // Restores files from the action's backup zip using relative paths.
    // The zip was created with baseDir preserved in action metadata.
    // Relative path = zip entry name → target = originalFilePath parent + entry
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
            // Retrieve the baseDir that was used when the zip was created.
            // This is the root we need to prepend to each zip entry name
            // to reconstruct the original absolute file path.
            // ------------------------------------------------------------------
            val baseDir = action.metadata["backup_base_dir"] ?: ""

            // ------------------------------------------------------------------
            // Check for conflicts: later non-undone actions that touched
            // the same files as this action
            // ------------------------------------------------------------------
            val laterActions = actionRepository.getActionsForProjectOnce(projectId)
                .filter { it.timestamp > action.timestamp && !it.undone }

            val myFiles = action.changedFiles.map { it.filePath }.toSet()
            val conflictingActions = laterActions.filter { laterAction ->
                laterAction.changedFiles.any { it.filePath in myFiles }
            }

            // ------------------------------------------------------------------
            // Extract zip to a temp directory
            // ------------------------------------------------------------------
            val restoreDir = File(
                backupZip.parentFile,
                "undo_temp_${System.currentTimeMillis()}"
            )

            val extracted = FileUtil.extractZip(backupZip, restoreDir)
            if (!extracted) {
                return@withContext UndoResult.Failure(
                    "Failed to extract backup zip: ${backupZip.absolutePath}"
                )
            }

            val restoredFiles = mutableListOf<String>()

            // ------------------------------------------------------------------
            // Restore each file using its original absolute path.
            //
            // The zip entry name is the path relative to baseDir.
            // We reconstruct: File(restoreDir, relativeEntry)
            // and copy it back to: File(snapshot.filePath)
            //
            // For CREATED files (did not exist before this action), we delete
            // them on undo to restore the pre-action state.
            // ------------------------------------------------------------------
            for (snapshot in action.changedFiles) {
                val targetFile = File(snapshot.filePath)

                when (snapshot.operation) {
                    FileOperation.CREATED -> {
                        // This file was created by the action — remove it on undo
                        if (targetFile.exists()) {
                            targetFile.delete()
                            restoredFiles.add(snapshot.filePath)
                        }
                    }

                    FileOperation.MODIFIED -> {
                        // Compute the zip entry name = relative path from baseDir
                        val relativeEntry = if (baseDir.isNotEmpty()) {
                            targetFile.canonicalPath
                                .removePrefix(baseDir)
                                .trimStart('/', '\\')
                        } else {
                            targetFile.name
                        }

                        val backedUpFile = File(restoreDir, relativeEntry)
                        if (backedUpFile.exists()) {
                            targetFile.parentFile?.mkdirs()
                            backedUpFile.copyTo(targetFile, overwrite = true)
                            restoredFiles.add(snapshot.filePath)
                        } else {
                            // Entry not found in zip — log but continue
                            android.util.Log.w(
                                "BackupOrchestrator",
                                "Backup entry not found for restore: $relativeEntry"
                            )
                        }
                    }

                    FileOperation.DELETED -> {
                        // File was deleted by action.
                        // On undo we want it gone again — nothing to do.
                    }
                }
            }

            // Clean up temp extraction directory
            restoreDir.deleteRecursively()

            // Mark action as undone in Room (never deleted — audit trail)
            actionRepository.markActionUndone(actionId)

            return@withContext if (conflictingActions.isNotEmpty()) {
                UndoResult.ConflictWarning(
                    restoredFiles = restoredFiles,
                    conflictingActions = conflictingActions
                )
            } else {
                UndoResult.Success(restoredFiles)
            }

        } catch (e: Exception) {
            UndoResult.Failure(
                "Unexpected error during undo: ${e.message}", e
            )
        }
    }

    // -------------------------------------------------------------------------
    // Full project snapshot — zip entire source directory
    // Used for project export and before major operations (full cleanup pass)
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
                baseDir = sourceDir.canonicalPath  // preserve full structure in zip
            )
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // Restore a full project snapshot
    // Used after factory reset + reimport workflow
    // -------------------------------------------------------------------------

    suspend fun restoreFullSnapshot(
        snapshotZip: File,
        targetDir: File
    ): Boolean = withContext(Dispatchers.IO) {
        FileUtil.extractZip(snapshotZip, targetDir)
    }
}

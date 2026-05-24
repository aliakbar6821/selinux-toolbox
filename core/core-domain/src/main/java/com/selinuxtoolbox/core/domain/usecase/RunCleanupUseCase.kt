package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.analyzer.CleanupEngine
import com.selinuxtoolbox.core.domain.analyzer.CleanupResult
import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.domain.repository.BackupOrchestrator
import com.selinuxtoolbox.core.domain.repository.BackupResult
import com.selinuxtoolbox.core.model.ActionType
import com.selinuxtoolbox.core.model.LoadedPolicy
import com.selinuxtoolbox.core.model.OperationProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject

sealed class CleanupPhase {
    data class Analyzing(val progress: OperationProgress) : CleanupPhase()
    data class Complete(val result: CleanupResult) : CleanupPhase()
    data class ApplyingChanges(val progress: OperationProgress) : CleanupPhase()
    data class Saved(
        val actionId: Long,
        val outputDir: String,
        val filesModified: Int
    ) : CleanupPhase()
    data class Failed(val reason: String) : CleanupPhase()
}

class RunCleanupUseCase @Inject constructor(
    private val cleanupEngine: CleanupEngine,
    private val backupOrchestrator: BackupOrchestrator,
    private val actionRepository: ActionRepository,
    private val appPreferences: AppPreferences
) {
    // Phase 1: analyze only — returns CleanupResult for user to review
    fun analyze(
        policy: LoadedPolicy,
        socPrefixes: List<String> = emptyList(),
        romPrefixes: List<String> = emptyList()
    ): Flow<CleanupPhase> = flow {
        cleanupEngine.analyze(policy, socPrefixes, romPrefixes).collect { (progress, result) ->
            if (result != null) {
                emit(CleanupPhase.Complete(result))
            } else {
                emit(CleanupPhase.Analyzing(progress))
            }
        }
    }

    // Phase 2: apply the cleanup result to output files on SD card
    // Called only after user reviews and confirms
    suspend fun applyAndSave(
        projectId: Long,
        policy: LoadedPolicy,
        result: CleanupResult,
        projectFolderPath: String
    ): CleanupPhase {
        return try {
            val outputBasePath = appPreferences.outputFolderPath.first()
            val outputDir = "$projectFolderPath/output"

            // Build list of files that will be modified
            val filesToModify = result.cleanedFileContents.keys
                .filter { result.cleanedFileContents[it] != CleanupEngine.UNCHANGED_MARKER }
                .map { File(it) }

            if (filesToModify.isEmpty()) {
                return CleanupPhase.Saved(
                    actionId = -1L,
                    outputDir = outputDir,
                    filesModified = 0
                )
            }

            val backupResult = backupOrchestrator.executeWithBackup(
                projectId = projectId,
                actionType = ActionType.CLEANUP_PASS,
                description = "Cleanup pass: removed ${result.orphanedTypes.size} orphaned, " +
                              "${result.wrongSocTypes.size} wrong-SoC, " +
                              "${result.wrongRomTypes.size} wrong-ROM type declarations",
                projectOutputDir = projectFolderPath,
                filesToModify = filesToModify,
                metadata = mapOf(
                    "orphaned_count"  to result.orphanedTypes.size.toString(),
                    "wrong_soc_count" to result.wrongSocTypes.size.toString(),
                    "wrong_rom_count" to result.wrongRomTypes.size.toString(),
                    "total_rules"     to result.totalRules.toString()
                )
            ) {
                // The action lambda: produce Map<File, newContent>
                val outputMap = mutableMapOf<File, String>()

                result.cleanedFileContents.forEach { (originalPath, patchDescriptor) ->
                    if (patchDescriptor == CleanupEngine.UNCHANGED_MARKER) return@forEach

                    val originalFile = File(originalPath)
                    if (!originalFile.exists()) return@forEach

                    val rawContent = originalFile.readText()
                    val cleanedContent = cleanupEngine.applyPatch(rawContent, patchDescriptor)

                    // Write to output dir (not original partition — constraint #3)
                    val relativePath = originalPath
                        .substringAfter("/etc/selinux/")
                        .ifEmpty { originalFile.name }
                    val outputFile = File(outputDir, relativePath)
                    outputMap[outputFile] = cleanedContent
                }

                outputMap
            }

            when (backupResult) {
                is BackupResult.Success -> CleanupPhase.Saved(
                    actionId = backupResult.actionId,
                    outputDir = outputDir,
                    filesModified = backupResult.changedFiles.size
                )
                is BackupResult.Failure -> CleanupPhase.Failed(backupResult.reason)
            }
        } catch (e: Exception) {
            CleanupPhase.Failed("Unexpected error: ${e.message}")
        }
    }
}

package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.domain.repository.BackupOrchestrator
import java.io.File
import javax.inject.Inject

class ExportProjectUseCase @Inject constructor(
    private val actionRepository: ActionRepository,
    private val backupOrchestrator: BackupOrchestrator
) {
    suspend operator fun invoke(
        projectId: Long,
        exportsDir: String
    ): Result<String> = runCatching {
        val project = actionRepository.getProjectById(projectId)
            ?: error("Project $projectId not found")

        val sourceDir = File(project.projectFolderPath)
        if (!sourceDir.exists()) error("Project folder not found: ${project.projectFolderPath}")

        val timestamp = System.currentTimeMillis()
        val safeName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputZip = File(exportsDir, "${safeName}_${timestamp}.zip")
        outputZip.parentFile?.mkdirs()

        val ok = backupOrchestrator.createFullSnapshot(sourceDir, outputZip)
        if (!ok) error("Failed to create export zip")

        outputZip.absolutePath
    }
}

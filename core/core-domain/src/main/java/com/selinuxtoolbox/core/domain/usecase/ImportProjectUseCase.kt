package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.domain.repository.BackupOrchestrator
import com.selinuxtoolbox.core.model.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File
import javax.inject.Inject

class ImportProjectUseCase @Inject constructor(
    private val backupOrchestrator: BackupOrchestrator,
    private val actionRepository: ActionRepository
) {
    // Import a project zip exported by ExportProjectUseCase.
    // Extracts to projectsBaseDir/<projectName>/
    // Reads project.json from the extracted folder to get metadata.
    // Returns the new or existing project ID.
    suspend operator fun invoke(
        zipPath: String,
        projectsBaseDir: String   // e.g. /sdcard/SELinuxToolbox/projects
    ): Result<Long> = runCatching {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) error("Import zip not found: $zipPath")

        // Extract to a temp dir first to read project.json
        val tempDir = File(projectsBaseDir, "import_temp_${System.currentTimeMillis()}")
        val extracted = backupOrchestrator.restoreFullSnapshot(zipFile, tempDir)
        if (!extracted) error("Failed to extract import zip")

        // Read project.json for metadata
        val projectJson = File(tempDir, "project.json")
        val json = Json { ignoreUnknownKeys = true }

        val project: Project? = if (projectJson.exists()) {
            try {
                json.decodeFromString<Project>(projectJson.readText())
            } catch (e: Exception) {
                null
            }
        } else null

        // Derive project name from metadata or zip filename
        val projectName = project?.name
            ?: zipFile.nameWithoutExtension.replace(Regex("_\\d+$"), "")

        // Move extracted content to final project dir
        val finalDir = File(projectsBaseDir, projectName)
        if (finalDir.exists()) finalDir.deleteRecursively()
        tempDir.renameTo(finalDir)

        // Check if a project with this ID already exists in Room
        val existingById = project?.id?.let { actionRepository.getProjectById(it) }

        if (existingById != null) {
            // Project already in Room — just return existing ID
            existingById.id
        } else {
            // Create a new Room record for this imported project
            actionRepository.createProject(
                name = projectName,
                sourceDevice = project?.sourceDevice ?: "",
                targetDevice = project?.targetDevice ?: "",
                sourceRom = project?.sourceRom ?: "",
                targetRom = project?.targetRom ?: "",
                projectFolderPath = finalDir.absolutePath
            )
        }
    }
}

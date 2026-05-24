package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.domain.repository.BackupOrchestrator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import javax.inject.Inject

class ImportProjectUseCase @Inject constructor(
    private val backupOrchestrator: BackupOrchestrator,
    private val actionRepository: ActionRepository
) {
    suspend operator fun invoke(
        zipPath: String,
        projectsBaseDir: String
    ): Result<Long> = runCatching {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) error("Import zip not found: $zipPath")

        val tempDir = File(projectsBaseDir, "import_temp_${System.currentTimeMillis()}")
        val extracted = backupOrchestrator.restoreFullSnapshot(zipFile, tempDir)
        if (!extracted) error("Failed to extract import zip")

        // Parse project.json manually using JsonObject to avoid
        // reified inline deserialization issues with KSP
        val projectJsonFile = File(tempDir, "project.json")
        val json = Json { ignoreUnknownKeys = true }

        var projectName: String? = null
        var projectId: Long? = null
        var sourceDevice = ""
        var targetDevice = ""
        var sourceRom = ""
        var targetRom = ""

        if (projectJsonFile.exists()) {
            try {
                val obj: JsonObject = json.parseToJsonElement(
                    projectJsonFile.readText()
                ).jsonObject
                projectName = obj["name"]?.jsonPrimitive?.content
                projectId = obj["id"]?.jsonPrimitive?.longOrNull
                sourceDevice = obj["sourceDevice"]?.jsonPrimitive?.content ?: ""
                targetDevice = obj["targetDevice"]?.jsonPrimitive?.content ?: ""
                sourceRom = obj["sourceRom"]?.jsonPrimitive?.content ?: ""
                targetRom = obj["targetRom"]?.jsonPrimitive?.content ?: ""
            } catch (e: Exception) {
                // Malformed project.json — continue with defaults
            }
        }

        val name = projectName
            ?: zipFile.nameWithoutExtension.replace(Regex("_\\d+$"), "")

        val finalDir = File(projectsBaseDir, name)
        if (finalDir.exists()) finalDir.deleteRecursively()
        tempDir.renameTo(finalDir)

        // Check if project with same ID already exists in Room
        val existing = projectId?.let { actionRepository.getProjectById(it) }
        if (existing != null) {
            existing.id
        } else {
            actionRepository.createProject(
                name = name,
                sourceDevice = sourceDevice,
                targetDevice = targetDevice,
                sourceRom = sourceRom,
                targetRom = targetRom,
                projectFolderPath = finalDir.absolutePath
            )
        }
    }
}

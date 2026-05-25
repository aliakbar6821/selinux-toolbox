package com.selinuxtoolbox.core.domain.repository

import com.selinuxtoolbox.core.data.db.ActionDao
import com.selinuxtoolbox.core.data.db.ActionEntity
import com.selinuxtoolbox.core.data.db.NoteDao
import com.selinuxtoolbox.core.data.db.NoteEntity
import com.selinuxtoolbox.core.data.db.ProjectDao
import com.selinuxtoolbox.core.data.db.ProjectEntity
import com.selinuxtoolbox.core.data.util.FileUtil
import com.selinuxtoolbox.core.model.ActionRecord
import com.selinuxtoolbox.core.model.ActionType
import com.selinuxtoolbox.core.model.ActionValidity
import com.selinuxtoolbox.core.model.ActionValidation
import com.selinuxtoolbox.core.model.ActiveMode
import com.selinuxtoolbox.core.model.FileOperation
import com.selinuxtoolbox.core.model.FileSnapshot
import com.selinuxtoolbox.core.model.Project
import com.selinuxtoolbox.core.model.ProjectNote
import com.selinuxtoolbox.core.model.ProjectStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val actionDao: ActionDao,
    private val noteDao: NoteDao
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // ── Project operations ────────────────────────────────────────────────────

    fun getAllProjects(): Flow<List<Project>> =
        projectDao.getAllProjects().map { entities -> entities.map { it.toProject() } }

    fun getActiveProject(): Flow<Project?> =
        projectDao.getActiveProject().map { it?.toProject() }

    suspend fun getProjectById(id: Long): Project? =
        projectDao.getById(id)?.toProject()

    suspend fun createProject(
        name: String,
        sourceDevice: String,
        targetDevice: String,
        sourceRom: String,
        targetRom: String,
        projectFolderPath: String,
        oemPath: String = "",
        aospPath: String = "",
        workPath: String = "",
        mappingVersion: String = "34.0"
    ): Long {
        val now = System.currentTimeMillis()
        val entity = ProjectEntity(
            id = 0,
            name = name,
            sourceDevice = sourceDevice,
            targetDevice = targetDevice,
            sourceRom = sourceRom,
            targetRom = targetRom,
            projectFolderPath = projectFolderPath,
            createdAt = now,
            lastModified = now,
            status = ProjectStatus.ACTIVE,
            oemPath = oemPath,
            aospPath = aospPath,
            workPath = workPath.ifBlank { projectFolderPath },
            mappingVersion = mappingVersion,
            activeMode = ActiveMode.OFFLINE
        )
        return projectDao.insert(entity)
    }

    suspend fun updateProject(project: Project) {
        projectDao.update(project.toEntity())
    }

    suspend fun archiveProject(projectId: Long) {
        projectDao.updateStatus(projectId, ProjectStatus.ARCHIVED.name)
    }

    suspend fun setActiveProject(projectId: Long) {
        projectDao.updateLastModified(projectId, System.currentTimeMillis())
    }

    suspend fun deleteProject(projectId: Long) {
        val entity = projectDao.getById(projectId) ?: return
        projectDao.delete(entity)
    }

    // ── Action logging ────────────────────────────────────────────────────────

    suspend fun recordActionStart(
        projectId: Long,
        type: ActionType,
        description: String,
        backupZipPath: String,
        metadata: Map<String, String> = emptyMap()
    ): Long {
        val entity = ActionEntity(
            id = 0,
            projectId = projectId,
            type = type,
            description = description,
            timestamp = System.currentTimeMillis(),
            backupZipPath = backupZipPath,
            changedFilesJson = "[]",
            metadataJson = json.encodeToString(metadata)
        )
        val actionId = actionDao.insert(entity)
        projectDao.updateLastModified(projectId, System.currentTimeMillis())
        return actionId
    }

    suspend fun recordActionComplete(actionId: Long, changedFiles: List<FileSnapshot>) {
        actionDao.updateChangedFiles(actionId, json.encodeToString(changedFiles))
    }

    fun getActionsForProject(projectId: Long): Flow<List<ActionRecord>> =
        actionDao.getActionsForProject(projectId).map { list ->
            list.map { it.toActionRecord() }
        }

    suspend fun getActionsForProjectOnce(projectId: Long): List<ActionRecord> =
        actionDao.getActionsForProjectOnce(projectId).map { it.toActionRecord() }

    suspend fun getActionById(id: Long): ActionRecord? =
        actionDao.getById(id)?.toActionRecord()

    suspend fun markActionUndone(actionId: Long) {
        actionDao.markUndone(actionId, System.currentTimeMillis())
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    fun getNotesForProject(projectId: Long): Flow<List<ProjectNote>> =
        noteDao.getNotesForProject(projectId).map { list ->
            list.map { it.toProjectNote() }
        }

    fun getProjectLevelNotes(projectId: Long): Flow<List<ProjectNote>> =
        noteDao.getProjectLevelNotes(projectId).map { list ->
            list.map { it.toProjectNote() }
        }

    fun getNotesForAction(projectId: Long, actionId: Long): Flow<List<ProjectNote>> =
        noteDao.getNotesForAction(projectId, actionId).map { list ->
            list.map { it.toProjectNote() }
        }

    suspend fun addNote(
        projectId: Long,
        content: String,
        tags: List<String> = emptyList(),
        actionId: Long? = null
    ): Long {
        val entity = NoteEntity(
            id = 0,
            projectId = projectId,
            actionId = actionId,
            content = content,
            timestamp = System.currentTimeMillis(),
            tagsJson = json.encodeToString(tags)
        )
        return noteDao.insert(entity)
    }

    suspend fun deleteNote(note: ProjectNote) {
        noteDao.delete(note.toEntity())
    }

    // ── Action validity ───────────────────────────────────────────────────────

    suspend fun validateActions(projectId: Long): List<ActionValidation> =
        withContext(Dispatchers.IO) {
            actionDao.getActionsForProjectOnce(projectId).map { entity ->
                val action = entity.toActionRecord()
                val (validity, explanation) = computeValidity(action)
                ActionValidation(action, validity, explanation)
            }
        }

    private fun computeValidity(action: ActionRecord): Pair<ActionValidity, String> {
        if (action.changedFiles.isEmpty()) {
            return ActionValidity.NOT_APPLICABLE to "No file changes recorded"
        }

        var allMatch    = true
        var allOriginal = true
        var anyMissing  = false
        var anyChanged  = false

        for (snapshot in action.changedFiles) {
            val file = File(snapshot.filePath)
            if (!file.exists()) {
                anyMissing  = true
                allMatch    = false
                allOriginal = false
            } else {
                val h = FileUtil.sha256(file.readText())
                if (h != snapshot.modifiedHash) allMatch    = false
                if (h != snapshot.originalHash) allOriginal = false
                if (h != snapshot.modifiedHash && h != snapshot.originalHash) anyChanged = true
            }
        }

        return when {
            anyMissing && action.changedFiles.all { !File(it.filePath).exists() } ->
                ActionValidity.NOT_APPLICABLE to "All target files no longer exist"
            anyMissing ->
                ActionValidity.PARTIALLY_APPLICABLE to "Some target files are missing"
            allMatch ->
                ActionValidity.ALREADY_APPLIED to "All files match modified state"
            allOriginal ->
                ActionValidity.NEEDS_REAPPLY to "Files match original — safe to re-apply"
            anyChanged ->
                ActionValidity.PARTIALLY_APPLICABLE to "Files changed — manual review required"
            else ->
                ActionValidity.PARTIALLY_APPLICABLE to "Mixed state — manual review required"
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun ProjectEntity.toProject() = Project(
        id                = id,
        name              = name,
        sourceDevice      = sourceDevice,
        targetDevice      = targetDevice,
        sourceRom         = sourceRom,
        targetRom         = targetRom,
        projectFolderPath = projectFolderPath,
        createdAt         = createdAt,
        lastModified      = lastModified,
        status            = status,
        oemPath           = oemPath,
        aospPath          = aospPath,
        workPath          = workPath,
        mappingVersion    = mappingVersion,
        activeMode        = activeMode
    )

    private fun Project.toEntity() = ProjectEntity(
        id                = id,
        name              = name,
        sourceDevice      = sourceDevice,
        targetDevice      = targetDevice,
        sourceRom         = sourceRom,
        targetRom         = targetRom,
        projectFolderPath = projectFolderPath,
        createdAt         = createdAt,
        lastModified      = lastModified,
        status            = status,
        oemPath           = oemPath,
        aospPath          = aospPath,
        workPath          = workPath,
        mappingVersion    = mappingVersion,
        activeMode        = activeMode
    )

    private fun ActionEntity.toActionRecord(): ActionRecord {
        val changedFiles = try {
            json.decodeFromString<List<FileSnapshot>>(changedFilesJson)
        } catch (e: Exception) { emptyList() }

        val metadata = try {
            json.decodeFromString<Map<String, String>>(metadataJson)
        } catch (e: Exception) { emptyMap() }

        return ActionRecord(
            id            = id,
            projectId     = projectId,
            type          = type,
            description   = description,
            timestamp     = timestamp,
            backupZipPath = backupZipPath,
            changedFiles  = changedFiles,
            undone        = undone,
            undoneAt      = undoneAt,
            metadata      = metadata
        )
    }

    private fun NoteEntity.toProjectNote(): ProjectNote {
        val tags = try {
            json.decodeFromString<List<String>>(tagsJson)
        } catch (e: Exception) { emptyList() }
        return ProjectNote(
            id        = id,
            projectId = projectId,
            actionId  = actionId,
            content   = content,
            timestamp = timestamp,
            tags      = tags
        )
    }

    private fun ProjectNote.toEntity() = NoteEntity(
        id        = id,
        projectId = projectId,
        actionId  = actionId,
        content   = content,
        timestamp = timestamp,
        tagsJson  = json.encodeToString(tags)
    )
}

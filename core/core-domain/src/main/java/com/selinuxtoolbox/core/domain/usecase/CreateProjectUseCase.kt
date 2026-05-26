package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.util.FileUtil
import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.model.ActiveMode
import java.io.File
import javax.inject.Inject

class CreateProjectUseCase @Inject constructor(
    private val actionRepository: ActionRepository
) {
    suspend operator fun invoke(
        name: String,
        sourceDevice: String,
        targetDevice: String,
        sourceRom: String,
        targetRom: String,
        outputBasePath: String,          // e.g. /sdcard/SELinuxToolbox
        mode: ActiveMode = ActiveMode.OFFLINE
    ): Result<Long> = runCatching {

        // 1. Ensure base dirs exist
        val dirs = FileUtil.ensureOutputDirs(outputBasePath)
        FileUtil.ensureProjectDirs(dirs.projects, name)

        val projectFolderPath = "${dirs.projects.absolutePath}/$name"

        // 2. For OFFLINE projects scaffold the full workspace tree
        //    /sdcard/SELinuxToolbox/projects/<name>/
        //      OEM/  AOSP/  work/  logs/
        val (oemPath, aospPath, workPath) = if (mode == ActiveMode.OFFLINE) {
            val workspace = FileUtil.ensureWorkspaceDirs(File(projectFolderPath))
            Triple(
                workspace.oem.absolutePath,
                workspace.aosp.absolutePath,
                workspace.work.absolutePath
            )
        } else {
            // LIVE mode — paths point to live device partitions, user sets them later
            Triple("", "", "$projectFolderPath/work")
        }

        // 3. Persist to DB
        actionRepository.createProject(
            name              = name,
            sourceDevice      = sourceDevice,
            targetDevice      = targetDevice,
            sourceRom         = sourceRom,
            targetRom         = targetRom,
            projectFolderPath = projectFolderPath,
            oemPath           = oemPath,
            aospPath          = aospPath,
            workPath          = workPath,
            mappingVersion    = "34.0"
        )
    }
}

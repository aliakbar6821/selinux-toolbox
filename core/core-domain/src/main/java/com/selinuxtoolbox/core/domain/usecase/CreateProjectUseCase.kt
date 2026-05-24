package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.util.FileUtil
import com.selinuxtoolbox.core.domain.repository.ActionRepository
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
        outputBasePath: String   // e.g. /sdcard/SELinuxToolbox
    ): Result<Long> = runCatching {
        // Ensure SD card directory structure exists for this project
        val dirs = FileUtil.ensureOutputDirs(outputBasePath)
        FileUtil.ensureProjectDirs(dirs.projects, name)

        val projectFolderPath = "${dirs.projects.absolutePath}/$name"

        actionRepository.createProject(
            name = name,
            sourceDevice = sourceDevice,
            targetDevice = targetDevice,
            sourceRom = sourceRom,
            targetRom = targetRom,
            projectFolderPath = projectFolderPath
        )
    }
}

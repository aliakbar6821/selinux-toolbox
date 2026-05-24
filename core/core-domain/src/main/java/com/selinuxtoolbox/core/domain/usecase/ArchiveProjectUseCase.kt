package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.repository.ActionRepository
import javax.inject.Inject

class ArchiveProjectUseCase @Inject constructor(
    private val actionRepository: ActionRepository
) {
    suspend operator fun invoke(projectId: Long): Result<Unit> = runCatching {
        actionRepository.archiveProject(projectId)
    }
}

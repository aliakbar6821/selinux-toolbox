package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.domain.repository.ActionRepository
import javax.inject.Inject

class SetActiveProjectUseCase @Inject constructor(
    private val actionRepository: ActionRepository,
    private val appPreferences: AppPreferences
) {
    suspend operator fun invoke(projectId: Long): Result<Unit> = runCatching {
        actionRepository.setActiveProject(projectId)
        appPreferences.setActiveProjectId(projectId)
    }
}

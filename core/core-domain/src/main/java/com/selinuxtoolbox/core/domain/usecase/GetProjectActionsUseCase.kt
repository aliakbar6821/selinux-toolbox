package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.model.ActionRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetProjectActionsUseCase @Inject constructor(
    private val repository: ActionRepository
) {
    operator fun invoke(projectId: Long): Flow<List<ActionRecord>> =
        repository.getActionsForProject(projectId)
}

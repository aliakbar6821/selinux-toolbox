package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.model.Project
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllProjectsUseCase @Inject constructor(
    private val actionRepository: ActionRepository
) {
    operator fun invoke(): Flow<List<Project>> =
        actionRepository.getAllProjects()
}

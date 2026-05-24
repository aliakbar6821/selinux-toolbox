package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.model.ActionValidation
import com.selinuxtoolbox.core.domain.repository.ActionRepository
import javax.inject.Inject

class ValidateActionsUseCase @Inject constructor(
    private val actionRepository: ActionRepository
) {
    // After factory reset + reimport: check which previous actions
    // are still valid, need reapply, or are no longer applicable.
    suspend operator fun invoke(projectId: Long): List<ActionValidation> =
        actionRepository.validateActions(projectId)
}

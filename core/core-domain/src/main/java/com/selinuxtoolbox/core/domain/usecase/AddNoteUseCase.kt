package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.repository.ActionRepository
import javax.inject.Inject

class AddNoteUseCase @Inject constructor(
    private val actionRepository: ActionRepository
) {
    suspend operator fun invoke(
        projectId: Long,
        content: String,
        tags: List<String> = emptyList(),
        actionId: Long? = null
    ): Result<Long> = runCatching {
        actionRepository.addNote(projectId, content, tags, actionId)
    }
}

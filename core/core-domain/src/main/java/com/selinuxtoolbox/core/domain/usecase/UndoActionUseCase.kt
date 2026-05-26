package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.repository.BackupOrchestrator
import com.selinuxtoolbox.core.domain.repository.UndoResult
import javax.inject.Inject

class UndoActionUseCase @Inject constructor(
    private val backupOrchestrator: BackupOrchestrator
) {
    suspend operator fun invoke(
        actionId: Long,
        projectId: Long
    ): UndoResult = backupOrchestrator.undoAction(actionId, projectId)
}

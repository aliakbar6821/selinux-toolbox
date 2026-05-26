package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.path.WorkspaceValidator
import com.selinuxtoolbox.core.model.WorkspaceValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ValidateWorkspaceUseCase @Inject constructor(
    private val validator: WorkspaceValidator
) {
    suspend operator fun invoke(
        oemPath: String,
        aospPath: String,
        workPath: String
    ): WorkspaceValidation = withContext(Dispatchers.IO) {
        validator.validate(oemPath, aospPath, workPath)
    }
}

package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.root.RootShell
import javax.inject.Inject

class SetSelinuxModeUseCase @Inject constructor(
    private val rootShell: RootShell
) {
    // Returns true if mode was set successfully
    suspend operator fun invoke(enforcing: Boolean): Boolean =
        rootShell.setSelinuxMode(enforcing)
}

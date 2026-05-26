package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.model.LoadedPolicy
import com.selinuxtoolbox.core.domain.repository.PolicyRepository
import javax.inject.Inject

class GetProtectedTypesUseCase @Inject constructor(
    private val policyRepository: PolicyRepository
) {
    // Returns all type names that appear in mapping/ files.
    // These must NEVER be removed or modified during cleanup.
    operator fun invoke(policy: LoadedPolicy): Set<String> =
        policyRepository.getProtectedTypes(policy)
}

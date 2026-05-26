package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.model.LoadedPolicy
import com.selinuxtoolbox.core.domain.repository.PolicyRepository
import javax.inject.Inject

class GetLivePolicyUseCase @Inject constructor(
    private val policyRepository: PolicyRepository
) {
    suspend operator fun invoke(): Result<LoadedPolicy> =
        policyRepository.loadLivePolicy()
}

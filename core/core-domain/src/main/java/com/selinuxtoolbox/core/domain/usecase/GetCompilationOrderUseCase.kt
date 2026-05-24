package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.model.CilFile
import com.selinuxtoolbox.core.model.LoadedPolicy
import com.selinuxtoolbox.core.domain.repository.PolicyRepository
import javax.inject.Inject

class GetCompilationOrderUseCase @Inject constructor(
    private val policyRepository: PolicyRepository
) {
    // Returns CIL files in the exact order secilc expects:
    // plat → mapping → system_ext → system_ext mapping →
    // product → plat_pub_versioned → vendor → odm
    operator fun invoke(policy: LoadedPolicy): List<CilFile> =
        policyRepository.getCompilationOrder(policy)
}

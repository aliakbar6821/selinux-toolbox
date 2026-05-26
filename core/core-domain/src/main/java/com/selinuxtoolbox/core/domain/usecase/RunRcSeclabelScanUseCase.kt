package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.analyzer.RcScanOutput
import com.selinuxtoolbox.core.domain.analyzer.RcScanResult
import com.selinuxtoolbox.core.domain.analyzer.RcSeclabelScanner
import com.selinuxtoolbox.core.domain.analyzer.SeclabelEntry
import com.selinuxtoolbox.core.domain.path.PolicyPartition
import com.selinuxtoolbox.core.model.ActiveMode
import java.io.File
import javax.inject.Inject

class RunRcSeclabelScanUseCase @Inject constructor(
    private val scanner: RcSeclabelScanner
) {
    // Phase 1: scan only — returns result for user to review
    suspend fun scan(
        oemPath: String,
        aospPath: String,
        mode: ActiveMode = ActiveMode.OFFLINE
    ): Result<RcScanResult> = runCatching {
        scanner.scan(oemPath, aospPath, mode)
    }

    // Phase 2: generate CIL from confirmed entries (user has reviewed)
    fun generateAndWrite(
        confirmedEntries: List<SeclabelEntry>,
        workPath: String
    ): Result<File> = runCatching {
        val outputs = scanner.generateOutput(confirmedEntries, workPath)
        scanner.writeOutputs(outputs, workPath)
    }
}

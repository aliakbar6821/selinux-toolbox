package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.root.RootShell
import com.selinuxtoolbox.core.domain.parser.AvcDenialParser
import com.selinuxtoolbox.core.model.AvcDenial
import com.selinuxtoolbox.core.model.DenialSource
import javax.inject.Inject

class GetRecentDenialsUseCase @Inject constructor(
    private val rootShell: RootShell,
    private val parser: AvcDenialParser
) {
    // One-shot: grab current dmesg and return all AVC denials found.
    // Used for initial load on dashboard. Limit to last N for performance.
    suspend operator fun invoke(limit: Int = 50): List<AvcDenial> {
        val result = rootShell.execute("dmesg 2>/dev/null | grep 'avc: ' | tail -$limit")
        return parser.parseLines(result.stdout, DenialSource.LIVE_DMESG)
            .takeLast(limit)
    }
}

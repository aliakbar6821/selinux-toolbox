package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.root.RootShell
import com.selinuxtoolbox.core.domain.parser.AvcDenialParser
import com.selinuxtoolbox.core.model.AvcDenial
import com.selinuxtoolbox.core.model.DenialSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class StreamAvcDenialsUseCase @Inject constructor(
    private val rootShell: RootShell,
    private val parser: AvcDenialParser
) {
    // Returns a Flow of AvcDenial parsed from live dmesg output.
    // Each emission is one denial (one permission).
    operator fun invoke(): Flow<AvcDenial> = flow {
        rootShell.streamAvcDenials().collect { line ->
            parser.parseLine(line, DenialSource.LIVE_DMESG).forEach { denial ->
                emit(denial)
            }
        }
    }.flowOn(Dispatchers.IO)
}

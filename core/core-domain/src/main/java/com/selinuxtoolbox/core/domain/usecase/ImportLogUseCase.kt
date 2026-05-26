package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.model.ImportedLog
import com.selinuxtoolbox.core.model.LogType
import com.selinuxtoolbox.core.domain.parser.AvcDenialParser
import com.selinuxtoolbox.core.model.DenialSource
import com.selinuxtoolbox.core.domain.path.PathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class ImportLogResult {
    data class Success(val log: ImportedLog) : ImportLogResult()
    data class Failure(val reason: String) : ImportLogResult()
}

class ImportLogUseCase @Inject constructor(
    private val pathResolver: PathResolver,
    private val avcParser: AvcDenialParser
) {
    suspend operator fun invoke(
        projectId: Long,
        workPath: String,
        sourceFile: File
    ): ImportLogResult = withContext(Dispatchers.IO) {
        try {
            val logsDir = pathResolver.importedLogsDir(workPath)
            logsDir.mkdirs()

            // Copy file into project logs dir with timestamp to avoid collision
            val ts = System.currentTimeMillis()
            val destFile = File(logsDir, "${ts}_${sourceFile.name}")
            sourceFile.copyTo(destFile, overwrite = true)

            val lines = destFile.readLines()
            val logType = detectLogType(lines)

            // Count each error type
            val source = when (logType) {
                LogType.LOGCAT -> DenialSource.LOGCAT
                else           -> DenialSource.IMPORTED_FILE
            }
            val avcCount          = lines.count { it.contains("avc:") && it.contains("denied") }
            val unmappedCount     = lines.count {
                it.contains("is not valid") && it.contains("context")
            }
            val undefinedCount    = lines.count {
                it.contains("is not defined") || it.contains("neverallow")
            }

            val log = ImportedLog(
                id                  = 0L, // Room will assign
                projectId           = projectId,
                fileName            = sourceFile.name,
                filePath            = destFile.absolutePath,
                importedAt          = ts,
                logType             = logType,
                totalLines          = lines.size,
                avcDenialCount      = avcCount,
                unmappedContextCount = unmappedCount,
                undefinedTypeCount  = undefinedCount
            )

            ImportLogResult.Success(log)
        } catch (e: Exception) {
            ImportLogResult.Failure("Failed to import log: ${e.message}")
        }
    }

    private fun detectLogType(lines: List<String>): LogType {
        val sample = lines.take(20).joinToString("\n")
        return when {
            sample.contains(Regex("^\\[\\s*\\d+\\.\\d+\\]", RegexOption.MULTILINE)) -> LogType.DMESG
            sample.contains(Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}", RegexOption.MULTILINE)) -> LogType.LOGCAT
            lines.any { it.contains("last_kmsg") || it.contains("Kernel panic") } -> LogType.LAST_KMSG
            else -> LogType.UNKNOWN
        }
    }
}

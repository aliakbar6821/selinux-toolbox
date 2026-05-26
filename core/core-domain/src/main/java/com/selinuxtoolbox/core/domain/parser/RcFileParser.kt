package com.selinuxtoolbox.core.domain.parser

data class RcService(
    val name: String,
    val executable: String,
    val seclabel: String?,      // the seclabel directive value
    val sourceFile: String,
    val lineNumber: Int
)

data class RcFile(
    val path: String,
    val services: List<RcService>
)

class RcFileParser {

    fun parse(content: String, sourceFile: String): RcFile {
        val services = mutableListOf<RcService>()
        val lines = content.lines()

        var currentServiceName: String? = null
        var currentExecutable: String? = null
        var currentSeclabel: String? = null
        var serviceStartLine = 0

        fun flushService() {
            val name = currentServiceName ?: return
            services.add(
                RcService(
                    name = name,
                    executable = currentExecutable ?: "",
                    seclabel = currentSeclabel,
                    sourceFile = sourceFile,
                    lineNumber = serviceStartLine
                )
            )
            currentServiceName = null
            currentExecutable = null
            currentSeclabel = null
        }

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            when {
                line.startsWith("service ") -> {
                    flushService()
                    val parts = line.split(Regex("\\s+"))
                    currentServiceName = parts.getOrNull(1) ?: ""
                    currentExecutable = parts.getOrNull(2) ?: ""
                    serviceStartLine = index + 1
                }
                line.startsWith("seclabel ") && currentServiceName != null -> {
                    val parts = line.split(Regex("\\s+"))
                    currentSeclabel = parts.getOrNull(1)
                }
                // New section keywords reset the current service context
                line.matches(Regex("^(on |import |subsystem |firmware_mounts_complete).*")) -> {
                    flushService()
                }
            }
        }

        flushService()
        return RcFile(path = sourceFile, services = services)
    }

    // Extract all seclabels from parsed RC files
    fun extractSeclabels(rcFiles: List<RcFile>): Map<String, String> {
        // Maps seclabel -> source file path
        val result = mutableMapOf<String, String>()
        rcFiles.forEach { rcFile ->
            rcFile.services.forEach { service ->
                service.seclabel?.let { label ->
                    result[label] = rcFile.path
                }
            }
        }
        return result
    }
}

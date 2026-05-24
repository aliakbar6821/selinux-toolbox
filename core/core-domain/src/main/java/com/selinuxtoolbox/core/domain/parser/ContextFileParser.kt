package com.selinuxtoolbox.core.domain.parser

import com.selinuxtoolbox.core.model.ContextEntry
import com.selinuxtoolbox.core.model.ContextFileType
import com.selinuxtoolbox.core.model.Partition
import com.selinuxtoolbox.core.model.SelinuxContext

class ContextFileParser {

    fun parse(
        content: String,
        sourceFile: String,
        fileType: ContextFileType,
        partition: Partition
    ): List<ContextEntry> {
        val entries = mutableListOf<ContextEntry>()
        content.lines().forEachIndexed { index, line ->
            val lineNumber = index + 1
            val trimmed = line.trim()

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            try {
                val entry = parseLine(trimmed, sourceFile, fileType, partition, lineNumber)
                if (entry != null) entries.add(entry)
            } catch (e: Exception) {
                // Skip malformed lines
            }
        }
        return entries
    }

    private fun parseLine(
        line: String,
        sourceFile: String,
        fileType: ContextFileType,
        partition: Partition,
        lineNumber: Int
    ): ContextEntry? {
        return when (fileType) {
            ContextFileType.FILE -> parseFileContext(line, sourceFile, partition, lineNumber)
            ContextFileType.SERVICE -> parseServiceContext(line, sourceFile, partition, lineNumber)
            ContextFileType.PROPERTY -> parsePropertyContext(line, sourceFile, partition, lineNumber)
            ContextFileType.HWSERVICE -> parseServiceContext(line, sourceFile, partition, lineNumber)
            ContextFileType.VNDSERVICE -> parseServiceContext(line, sourceFile, partition, lineNumber)
            ContextFileType.SEAPP -> parseSeappContext(line, sourceFile, partition, lineNumber)
            ContextFileType.PROCESS -> parseProcessContext(line, sourceFile, partition, lineNumber)
        }
    }

    // file_contexts format:
    // /path/pattern   u:object_r:type:s0
    // /path/pattern   --   u:object_r:type:s0   (with file type specifier)
    private fun parseFileContext(
        line: String,
        sourceFile: String,
        partition: Partition,
        lineNumber: Int
    ): ContextEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return null

        val pattern = parts[0]

        // Handle file type specifier (e.g. --, -d, -f, -l, -p, -s, -b, -c)
        val contextStr = when {
            parts.size >= 3 && parts[1].startsWith("-") -> parts[2]
            else -> parts[1]
        }

        if (contextStr == "<<none>>") return null

        val context = SelinuxContext.parse(contextStr) ?: return null
        return ContextEntry(
            pattern = pattern,
            context = contextStr,
            type = context.type,
            fileType = ContextFileType.FILE,
            sourceFile = sourceFile,
            lineNumber = lineNumber,
            partition = partition
        )
    }

    // service_contexts / hwservice_contexts / vndservice_contexts format:
    // service.name   u:object_r:type:s0
    private fun parseServiceContext(
        line: String,
        sourceFile: String,
        partition: Partition,
        lineNumber: Int
    ): ContextEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return null

        val name = parts[0]
        val contextStr = parts[1]
        val context = SelinuxContext.parse(contextStr) ?: return null

        return ContextEntry(
            pattern = name,
            context = contextStr,
            type = context.type,
            fileType = ContextFileType.SERVICE,
            sourceFile = sourceFile,
            lineNumber = lineNumber,
            partition = partition
        )
    }

    // property_contexts format:
    // property.name   u:object_r:type:s0
    private fun parsePropertyContext(
        line: String,
        sourceFile: String,
        partition: Partition,
        lineNumber: Int
    ): ContextEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return null

        val name = parts[0]
        val contextStr = parts[1]
        val context = SelinuxContext.parse(contextStr) ?: return null

        return ContextEntry(
            pattern = name,
            context = contextStr,
            type = context.type,
            fileType = ContextFileType.PROPERTY,
            sourceFile = sourceFile,
            lineNumber = lineNumber,
            partition = partition
        )
    }

    // seapp_contexts format:
    // user=_app seinfo=platform domain=platform_app type=app_data_file levelFrom=user
    private fun parseSeappContext(
        line: String,
        sourceFile: String,
        partition: Partition,
        lineNumber: Int
    ): ContextEntry? {
        // Extract domain= value as the type
        val domainMatch = Regex("domain=(\\S+)").find(line)
        val domain = domainMatch?.groupValues?.get(1) ?: return null

        return ContextEntry(
            pattern = line,
            context = "u:r:$domain:s0",
            type = domain,
            fileType = ContextFileType.SEAPP,
            sourceFile = sourceFile,
            lineNumber = lineNumber,
            partition = partition
        )
    }

    // Process context from ps -eZ output:
    // u:r:init:s0          root      1     0 /init
    private fun parseProcessContext(
        line: String,
        sourceFile: String,
        partition: Partition,
        lineNumber: Int
    ): ContextEntry? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return null

        val contextStr = parts[0]
        val context = SelinuxContext.parse(contextStr) ?: return null
        val processName = parts.getOrNull(7) ?: parts.lastOrNull() ?: ""

        return ContextEntry(
            pattern = processName,
            context = contextStr,
            type = context.type,
            fileType = ContextFileType.PROCESS,
            sourceFile = "live:ps",
            lineNumber = lineNumber,
            partition = Partition.SYSTEM
        )
    }
}

package com.selinuxtoolbox.core.domain.parser

import com.selinuxtoolbox.core.model.DenialMetadataEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DenialMetadataParser @Inject constructor() {

    fun parse(content: String): List<DenialMetadataEntry> {
        return content.lines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .mapNotNull { line -> parseLine(line.trim()) }
    }

    private fun parseLine(line: String): DenialMetadataEntry? {
        tryParseAosp(line)?.let { return it }
        tryParseQcom(line)?.let { return it }
        tryParseMinimal(line)?.let { return it }
        return null
    }

    private fun tryParseAosp(line: String): DenialMetadataEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 4) return null
        return try {
            DenialMetadataEntry(
                sourceDomain = parts[0],
                permission   = parts[1],
                targetType   = parts[2],
                objectClass  = parts[3],
                reason       = parts.getOrNull(4),
                rawLine      = line
            )
        } catch (e: Exception) { null }
    }

    private fun tryParseQcom(line: String): DenialMetadataEntry? {
        val cleaned = line.replace(Regex("\\[.*?\\]"), "").trim()
        return tryParseAosp(cleaned)?.copy(rawLine = line)
    }

    private fun tryParseMinimal(line: String): DenialMetadataEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return null
        return DenialMetadataEntry(
            sourceDomain = parts[0],
            permission   = "*",
            targetType   = parts[1],
            objectClass  = "*",
            reason       = null,
            rawLine      = line
        )
    }

    fun isDenialExpected(
        sourceDomain: String,
        permission: String,
        targetType: String,
        objectClass: String,
        metadata: List<DenialMetadataEntry>
    ): DenialMetadataEntry? {
        return metadata.firstOrNull { entry ->
            (entry.sourceDomain == sourceDomain || entry.sourceDomain == "*") &&
            (entry.permission   == permission   || entry.permission   == "*") &&
            (entry.targetType   == targetType   || entry.targetType   == "*") &&
            (entry.objectClass  == objectClass  || entry.objectClass  == "*")
        }
    }
}

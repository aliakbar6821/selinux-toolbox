package com.selinuxtoolbox.core.domain.parser

import com.selinuxtoolbox.core.model.DenialMetadataEntry

class DenialMetadataParser {

    fun parse(content: String): List<DenialMetadataEntry> {
        return content.lines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .mapNotNull { line -> parseLine(line.trim()) }
    }

    private fun parseLine(line: String): DenialMetadataEntry? {
        // Try multiple known formats

        // Format 1: AOSP standard
        // domain permission target_type target_class reason
        tryParseAosp(line)?.let { return it }

        // Format 2: QCOM extended
        // domain permission target_type target_class [extra] reason
        tryParseQcom(line)?.let { return it }

        // Format 3: Minimal (2-3 fields only)
        tryParseMinimal(line)?.let { return it }

        return null
    }

    // AOSP: 4-5 space-separated fields
    private fun tryParseAosp(line: String): DenialMetadataEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 4) return null

        return try {
            DenialMetadataEntry(
                sourceDomain = parts[0],
                permission = parts[1],
                targetType = parts[2],
                objectClass = parts[3],
                reason = parts.getOrNull(4),
                rawLine = line
            )
        } catch (e: Exception) { null }
    }

    // QCOM: may have extra bracketed fields
    private fun tryParseQcom(line: String): DenialMetadataEntry? {
        // Remove bracketed sections first
        val cleaned = line.replace(Regex("\\[.*?\\]"), "").trim()
        return tryParseAosp(cleaned)?.copy(rawLine = line)
    }

    // Minimal: just domain and type
    private fun tryParseMinimal(line: String): DenialMetadataEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return null
        return DenialMetadataEntry(
            sourceDomain = parts[0],
            permission = "*",
            targetType = parts[1],
            objectClass = "*",
            reason = null,
            rawLine = line
        )
    }

    // Check if a denial matches any metadata entry
    fun isDenialExpected(
        sourceDomain: String,
        permission: String,
        targetType: String,
        objectClass: String,
        metadata: List<DenialMetadataEntry>
    ): DenialMetadataEntry? {
        return metadata.firstOrNull { entry ->
            (entry.sourceDomain == sourceDomain || entry.sourceDomain == "*") &&
            (entry.permission == permission || entry.permission == "*") &&
            (entry.targetType == targetType || entry.targetType == "*") &&
            (entry.objectClass == objectClass || entry.objectClass == "*")
        }
    }
}

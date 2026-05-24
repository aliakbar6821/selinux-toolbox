package com.selinuxtoolbox.core.domain.parser

import com.selinuxtoolbox.core.model.AvcDenial
import com.selinuxtoolbox.core.model.DenialSource
import java.util.UUID

class AvcDenialParser {

    // Matches standard AVC denial lines from dmesg or logcat
    // avc: denied { read } for pid=1234 comm="mediaserver"
    //   name="audio" dev="tmpfs" ino=12345
    //   scontext=u:r:mediaserver:s0
    //   tcontext=u:object_r:vendor_audio_device:s0
    //   tclass=chr_file permissive=0

    private val AVC_PATTERN = Regex(
        """avc:\s+denied\s+\{([^}]+)\}.*?""" +
        """(?:pid=(\d+))?.*?""" +
        """(?:comm="([^"]*)")?.*?""" +
        """(?:name="([^"]*)")?.*?""" +
        """(?:path="([^"]*)")?.*?""" +
        """scontext=(\S+).*?""" +
        """tcontext=(\S+).*?""" +
        """tclass=(\S+)""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Alternative simpler pattern for single-line format
    private val AVC_SIMPLE = Regex(
        """avc:\s+denied\s+\{\s*(\w+(?:\s+\w+)*)\s*\}.*?scontext=(\S+)\s+tcontext=(\S+)\s+tclass=(\S+)"""
    )

    fun parseLine(line: String, source: DenialSource): AvcDenial? {
        // Try full pattern first
        val match = AVC_PATTERN.find(line) ?: AVC_SIMPLE.find(line)

        return when {
            match != null && match.groupValues.size >= 8 -> {
                parseFullMatch(match, line, source)
            }
            AVC_SIMPLE.containsMatchIn(line) -> {
                val m = AVC_SIMPLE.find(line)!!
                parseSimpleMatch(m, line, source)
            }
            else -> null
        }
    }

    private fun parseFullMatch(
        match: MatchResult,
        rawLine: String,
        source: DenialSource
    ): AvcDenial? {
        return try {
            val groups = match.groupValues
            val permissionsRaw = groups[1].trim()
            val permissions = permissionsRaw.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val pid = groups[2].toIntOrNull()
            val comm = groups[3].takeIf { it.isNotEmpty() }
            val name = groups[4].takeIf { it.isNotEmpty() }
            val path = groups[5].takeIf { it.isNotEmpty() }
            val scontext = groups[6]
            val tcontext = groups[7]
            val tclass = groups[8].trimEnd()

            val sourceDomain = extractType(scontext) ?: return null
            val targetType = extractType(tcontext) ?: return null

            // One denial per permission
            permissions.map { perm ->
                AvcDenial(
                    id = UUID.randomUUID().toString(),
                    sourceDomain = sourceDomain,
                    targetType = targetType,
                    objectClass = tclass,
                    permission = perm,
                    comm = comm,
                    path = path ?: name,
                    name = name,
                    pid = pid,
                    timestamp = null,
                    rawLine = rawLine,
                    source = source
                )
            }.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSimpleMatch(
        match: MatchResult,
        rawLine: String,
        source: DenialSource
    ): AvcDenial? {
        return try {
            val groups = match.groupValues
            val permissions = groups[1].trim().split(Regex("\\s+"))
            val scontext = groups[2]
            val tcontext = groups[3]
            val tclass = groups[4]

            val sourceDomain = extractType(scontext) ?: return null
            val targetType = extractType(tcontext) ?: return null

            AvcDenial(
                id = UUID.randomUUID().toString(),
                sourceDomain = sourceDomain,
                targetType = targetType,
                objectClass = tclass,
                permission = permissions.firstOrNull() ?: "",
                comm = null,
                path = null,
                name = null,
                pid = null,
                timestamp = null,
                rawLine = rawLine,
                source = source
            )
        } catch (e: Exception) {
            null
        }
    }

    // Parse multiple lines (from a file or dmesg dump)
    fun parseLines(lines: List<String>, source: DenialSource): List<AvcDenial> {
        return lines.mapNotNull { line ->
            if (line.contains("avc:") && line.contains("denied")) {
                parseLine(line, source)
            } else null
        }
    }

    // Extract type from SELinux context string u:r:type:s0
    private fun extractType(context: String): String? {
        val parts = context.split(":")
        return if (parts.size >= 3) parts[2] else null
    }

    // Group denials by source+target+class
    fun groupDenials(denials: List<AvcDenial>): Map<String, List<AvcDenial>> {
        return denials.groupBy { "${it.sourceDomain}:${it.targetType}:${it.objectClass}" }
    }
}

package com.selinuxtoolbox.core.domain.parser

import com.selinuxtoolbox.core.model.AvcDenial
import com.selinuxtoolbox.core.model.DenialSource
import java.util.UUID

class AvcDenialParser {

    // Matches standard AVC denial lines from dmesg or logcat.
    // Real examples:
    //   avc: denied { read } for pid=1234 comm="mediaserver" name="audio"
    //     scontext=u:r:mediaserver:s0 tcontext=u:object_r:vendor_audio_device:s0
    //     tclass=chr_file permissive=0
    //
    // Note: dmesg output may have kernel timestamp prefix like:
    //   [  123.456789] avc: denied ...
    // We strip that before matching.

    // Pattern for the full structured denial (handles multi-field lines)
    private val AVC_FULL = Regex(
        """avc:\s+denied\s+\{\s*([^}]+?)\s*\}""" +
        """(?:.*?pid=(\d+))?""" +
        """(?:.*?comm="([^"]*)")?""" +
        """(?:.*?name="([^"]*)")?""" +
        """(?:.*?path="([^"]*)")?""" +
        """.*?scontext=(\S+)""" +
        """.*?tcontext=(\S+)""" +
        """.*?tclass=(\w+)""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    // Simpler single-line fallback
    private val AVC_SIMPLE = Regex(
        """avc:\s+denied\s+\{\s*([^}]+?)\s*\}\s+.*?scontext=(\S+)\s+tcontext=(\S+)\s+tclass=(\w+)"""
    )

    fun parseLine(line: String, source: DenialSource): List<AvcDenial> {
        // Strip kernel timestamp if present: [  123.456789]
        val cleaned = line.replace(Regex("""^\[[\s\d.]+\]\s*"""), "").trim()

        if (!cleaned.contains("avc:") || !cleaned.contains("denied")) return emptyList()

        // Try full pattern
        val fullMatch = AVC_FULL.find(cleaned)
        if (fullMatch != null) {
            return parseFullMatch(fullMatch, line, source)
        }

        // Try simple pattern
        val simpleMatch = AVC_SIMPLE.find(cleaned)
        if (simpleMatch != null) {
            return parseSimpleMatch(simpleMatch, line, source)
        }

        return emptyList()
    }

    // FIX: return ALL permissions as separate AvcDenial objects, not just the first.
    // Each denial in the Room DB / UI represents one permission, which makes
    // grouping and rule generation correct.
    private fun parseFullMatch(
        match: MatchResult,
        rawLine: String,
        source: DenialSource
    ): List<AvcDenial> {
        return try {
            val groups = match.groupValues
            val permissionsRaw = groups[1].trim()
            val permissions = permissionsRaw
                .split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val pid    = groups[2].toIntOrNull()
            val comm   = groups[3].takeIf { it.isNotEmpty() }
            val name   = groups[4].takeIf { it.isNotEmpty() }
            val path   = groups[5].takeIf { it.isNotEmpty() }
            val sctx   = groups[6]
            val tctx   = groups[7]
            val tclass = groups[8].trim()

            val sourceDomain = extractType(sctx) ?: return emptyList()
            val targetType   = extractType(tctx) ?: return emptyList()

            // One AvcDenial per permission — this is what makes grouping and
            // rule suggestion correct downstream
            permissions.map { perm ->
                AvcDenial(
                    id           = UUID.randomUUID().toString(),
                    sourceDomain = sourceDomain,
                    targetType   = targetType,
                    objectClass  = tclass,
                    permission   = perm,
                    comm         = comm,
                    path         = path ?: name,
                    name         = name,
                    pid          = pid,
                    timestamp    = null,
                    rawLine      = rawLine,
                    source       = source
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSimpleMatch(
        match: MatchResult,
        rawLine: String,
        source: DenialSource
    ): List<AvcDenial> {
        return try {
            val groups      = match.groupValues
            val permissions = groups[1].trim()
                .split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val sctx   = groups[2]
            val tctx   = groups[3]
            val tclass = groups[4].trim()

            val sourceDomain = extractType(sctx) ?: return emptyList()
            val targetType   = extractType(tctx) ?: return emptyList()

            permissions.map { perm ->
                AvcDenial(
                    id           = UUID.randomUUID().toString(),
                    sourceDomain = sourceDomain,
                    targetType   = targetType,
                    objectClass  = tclass,
                    permission   = perm,
                    comm         = null,
                    path         = null,
                    name         = null,
                    pid          = null,
                    timestamp    = null,
                    rawLine      = rawLine,
                    source       = source
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Parse a list of lines (file import or dmesg dump).
    // Returns flat list of all AvcDenial objects (one per permission per line).
    fun parseLines(lines: List<String>, source: DenialSource): List<AvcDenial> {
        return lines.flatMap { line ->
            parseLine(line, source)
        }
    }

    // Extract type component from SELinux context string.
    // u:r:mediaserver:s0  →  mediaserver
    // u:object_r:vendor_audio_device:s0  →  vendor_audio_device
    private fun extractType(context: String): String? {
        val parts = context.split(":")
        return if (parts.size >= 3) parts[2].trim() else null
    }

    // Group denials by (sourceDomain, targetType, objectClass) key.
    // All permissions for the same triple are collected under one key.
    // This is used by the UI and rule suggestion engine.
    fun groupDenials(denials: List<AvcDenial>): Map<String, List<AvcDenial>> {
        return denials.groupBy { "${it.sourceDomain}::${it.targetType}::${it.objectClass}" }
    }

    // Build a minimal CIL allow rule from a group of denials.
    // Example: (allow mediaserver vendor_audio_device (chr_file (read write open)))
    fun buildCilRule(
        sourceDomain: String,
        targetType: String,
        objectClass: String,
        permissions: List<String>
    ): String {
        val permsStr = permissions.distinct().sorted().joinToString(" ")
        return "(allow $sourceDomain $targetType ($objectClass ($permsStr)))"
    }
}

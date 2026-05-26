package com.selinuxtoolbox.core.domain.parser

import com.selinuxtoolbox.core.model.AvcDenial
import com.selinuxtoolbox.core.model.DenialSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvcDenialParser @Inject constructor() {

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

    private val AVC_SIMPLE = Regex(
        """avc:\s+denied\s+\{\s*([^}]+?)\s*\}\s+.*?scontext=(\S+)\s+tcontext=(\S+)\s+tclass=(\w+)"""
    )

    fun parseLine(line: String, source: DenialSource): List<AvcDenial> {
        val cleaned = line.replace(Regex("""^\[[\s\d.]+\]\s*"""), "").trim()
        if (!cleaned.contains("avc:") || !cleaned.contains("denied")) return emptyList()

        val fullMatch = AVC_FULL.find(cleaned)
        if (fullMatch != null) return parseFullMatch(fullMatch, line, source)

        val simpleMatch = AVC_SIMPLE.find(cleaned)
        if (simpleMatch != null) return parseSimpleMatch(simpleMatch, line, source)

        return emptyList()
    }

    private fun parseFullMatch(
        match: MatchResult,
        rawLine: String,
        source: DenialSource
    ): List<AvcDenial> {
        return try {
            val groups      = match.groupValues
            val permissions = groups[1].trim().split(Regex("\\s+"))
                .map { it.trim() }.filter { it.isNotEmpty() }
            val pid    = groups[2].toIntOrNull()
            val comm   = groups[3].takeIf { it.isNotEmpty() }
            val name   = groups[4].takeIf { it.isNotEmpty() }
            val path   = groups[5].takeIf { it.isNotEmpty() }
            val sctx   = groups[6]
            val tctx   = groups[7]
            val tclass = groups[8].trim()

            val sourceDomain = extractType(sctx) ?: return emptyList()
            val targetType   = extractType(tctx) ?: return emptyList()

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
        } catch (e: Exception) { emptyList() }
    }

    private fun parseSimpleMatch(
        match: MatchResult,
        rawLine: String,
        source: DenialSource
    ): List<AvcDenial> {
        return try {
            val groups      = match.groupValues
            val permissions = groups[1].trim().split(Regex("\\s+"))
                .map { it.trim() }.filter { it.isNotEmpty() }
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
        } catch (e: Exception) { emptyList() }
    }

    fun parseLines(lines: List<String>, source: DenialSource): List<AvcDenial> =
        lines.flatMap { parseLine(it, source) }

    fun groupDenials(denials: List<AvcDenial>): Map<String, List<AvcDenial>> =
        denials.groupBy { "${it.sourceDomain}::${it.targetType}::${it.objectClass}" }

    fun buildCilRule(
        sourceDomain: String,
        targetType: String,
        objectClass: String,
        permissions: List<String>
    ): String {
        val permsStr = permissions.distinct().sorted().joinToString(" ")
        return "(allow $sourceDomain $targetType ($objectClass ($permsStr)))"
    }

    private fun extractType(context: String): String? {
        val parts = context.split(":")
        return if (parts.size >= 3) parts[2].trim() else null
    }
}

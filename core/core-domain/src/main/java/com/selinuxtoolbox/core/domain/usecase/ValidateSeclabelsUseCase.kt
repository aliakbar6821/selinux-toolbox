package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.domain.parser.RcFileParser
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.domain.path.PolicyPartition
import com.selinuxtoolbox.core.model.ActiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class SeclabelStatus { VALID, MISSING, SUSPICIOUS }

data class SeclabelCheck(
    val serviceName: String,
    val domain: String,
    val rcFile: String,
    val execPath: String?,
    val status: SeclabelStatus,
    val note: String
)

data class SeclabelValidationResult(
    val checks: List<SeclabelCheck>,
    val validCount: Int,
    val missingCount: Int,
    val suspiciousCount: Int
)

@Singleton
class ValidateSeclabelsUseCase @Inject constructor(
    private val rcFileParser: RcFileParser,
    private val pathResolver: PathResolver
) {
    private val RC_PARTITIONS = listOf(
        PolicyPartition.SYSTEM_EXT,
        PolicyPartition.VENDOR,
        PolicyPartition.ODM,
        PolicyPartition.PRODUCT
    )

    suspend operator fun invoke(
        oemPath: String,
        aospPath: String,
        workPath: String,
        mode: ActiveMode
    ): SeclabelValidationResult = withContext(Dispatchers.IO) {

        val knownTypes = loadAllKnownTypes(aospPath, workPath, mode)
        val checks     = mutableListOf<SeclabelCheck>()

        for (partition in RC_PARTITIONS) {
            val rcFiles = pathResolver.allRcFiles(oemPath, partition, mode)
            for (rcFile in rcFiles) {
                val content  = try { rcFile.readText() } catch (e: Exception) { continue }
                val parsed   = rcFileParser.parse(content, rcFile.absolutePath)

                for (service in parsed.services) {
                    val seclabel = service.seclabel ?: continue
                    val domain   = extractDomain(seclabel) ?: continue

                    val status = when {
                        domain !in knownTypes -> SeclabelStatus.MISSING
                        "${domain}_exec" !in knownTypes -> SeclabelStatus.SUSPICIOUS
                        else -> SeclabelStatus.VALID
                    }

                    val note = when (status) {
                        SeclabelStatus.VALID      -> "Domain and exec type found in policy"
                        SeclabelStatus.MISSING    -> "Domain '$domain' not found in any CIL"
                        SeclabelStatus.SUSPICIOUS -> "'${domain}_exec' is missing — run RC Seclabel fixer"
                    }

                    // RcService has: name, execPath (may be null), seclabel
                    checks.add(
                        SeclabelCheck(
                            serviceName = service.name,
                            domain      = domain,
                            rcFile      = rcFile.name,
                            execPath    = service.execPath,
                            status      = status,
                            note        = note
                        )
                    )
                }
            }
        }

        val sorted = checks.sortedWith(compareBy {
            when (it.status) {
                SeclabelStatus.MISSING    -> 0
                SeclabelStatus.SUSPICIOUS -> 1
                SeclabelStatus.VALID      -> 2
            }
        })

        SeclabelValidationResult(
            checks          = sorted,
            validCount      = sorted.count { it.status == SeclabelStatus.VALID },
            missingCount    = sorted.count { it.status == SeclabelStatus.MISSING },
            suspiciousCount = sorted.count { it.status == SeclabelStatus.SUSPICIOUS }
        )
    }

    private fun loadAllKnownTypes(
        aospPath: String,
        workPath: String,
        mode: ActiveMode
    ): Set<String> {
        val types   = mutableSetOf<String>()
        val pattern = Regex("""\(type\s+(\w+)\)""")

        PolicyPartition.values().forEach { partition ->
            pathResolver.allCilFiles(aospPath, partition, mode).forEach { file ->
                if (file.exists()) pattern.findAll(file.readText())
                    .forEach { types.add(it.groupValues[1]) }
            }
        }

        val outputsDir = File(workPath, "outputs")
        if (outputsDir.exists()) {
            outputsDir.walkTopDown()
                .filter { it.isFile && it.extension == "cil" }
                .forEach { file ->
                    pattern.findAll(file.readText())
                        .forEach { types.add(it.groupValues[1]) }
                }
        }

        return types
    }

    private fun extractDomain(seclabel: String): String? {
        val parts = seclabel.trim().split(":")
        return if (parts.size >= 3) parts[2].trim() else null
    }
}

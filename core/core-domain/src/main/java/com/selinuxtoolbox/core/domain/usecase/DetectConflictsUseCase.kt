package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.util.FileUtil
import com.selinuxtoolbox.core.domain.parser.CilParser
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.domain.path.PolicyPartition
import com.selinuxtoolbox.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ConflictReport(
    val duplicateTypes: List<Conflict>,
    val duplicateRules: List<Conflict>,
    val contradictingRules: List<Conflict>,
    val mappingMismatches: List<Conflict>,
    val missingPlatformTypes: List<Conflict>,
    val totalConflicts: Int,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int
)

data class Conflict(
    val type: ConflictType,
    val severity: ConflictSeverity,
    val description: String,
    val file1: String,
    val file2: String?,
    val line1: Int,
    val line2: Int?,
    val statement1: CilStatement?,
    val statement2: CilStatement?
)

@Singleton
class DetectConflictsUseCase @Inject constructor(
    private val pathResolver: PathResolver
) {

    suspend operator fun invoke(
        oemPath: String,
        aospPath: String,
        workPath: String,
        mappingVersion: String = "34.0"
    ): ConflictReport = withContext(Dispatchers.IO) {

        val conflicts = mutableListOf<Conflict>()

        // 1. Collect all CIL files from OEM and AOSP
        val oemFiles = mutableListOf<CilFile>()
        val aospFiles = mutableListOf<CilFile>()

        PolicyPartition.values().forEach { partition ->
            pathResolver.allCilFiles(oemPath, partition, ActiveMode.OFFLINE).forEach { file ->
                val parser = CilParser(file.absolutePath, partition.toPartition())
                val statements = parser.parse(file.readText())
                oemFiles.add(
                    CilFile(
                        partition = partition.toPartition(),
                        absolutePath = file.absolutePath,
                        relativePath = file.absolutePath.removePrefix(oemPath).trimStart('/'),
                        isMapping = false,
                        sha256 = FileUtil.sha256(file.readText()),
                        statements = statements
                    )
                )
            }

            pathResolver.allCilFiles(aospPath, partition, ActiveMode.OFFLINE).forEach { file ->
                val parser = CilParser(file.absolutePath, partition.toPartition())
                val statements = parser.parse(file.readText())
                aospFiles.add(
                    CilFile(
                        partition = partition.toPartition(),
                        absolutePath = file.absolutePath,
                        relativePath = file.absolutePath.removePrefix(aospPath).trimStart('/'),
                        isMapping = file.absolutePath.contains("/mapping/"),
                        sha256 = FileUtil.sha256(file.readText()),
                        statements = statements
                    )
                )
            }
        }

        // 2. Duplicate type declarations (same type declared in two files)
        val typeMap = mutableMapOf<String, MutableList<Pair<CilFile, CilStatement.TypeDeclaration>>>()
        (oemFiles + aospFiles).forEach { file ->
            file.statements.filterIsInstance<CilStatement.TypeDeclaration>().forEach { stmt ->
                typeMap.getOrPut(stmt.name) { mutableListOf() }.add(file to stmt)
            }
        }
        typeMap.forEach { (typeName, declarations) ->
            if (declarations.size > 1) {
                declarations.forEachIndexed { i, (file1, stmt1) ->
                    declarations.drop(i + 1).forEach { (file2, stmt2) ->
                        conflicts.add(
                            Conflict(
                                type = ConflictType.DUPLICATE_TYPE,
                                severity = ConflictSeverity.MEDIUM,
                                description = "Type '$typeName' declared in both ${file1.absolutePath} and ${file2.absolutePath}",
                                file1 = file1.absolutePath,
                                file2 = file2.absolutePath,
                                line1 = stmt1.lineNumber,
                                line2 = stmt2.lineNumber,
                                statement1 = stmt1,
                                statement2 = stmt2
                            )
                        )
                    }
                }
            }
        }

        // 3. Duplicate allow rules
        val ruleMap = mutableMapOf<String, MutableList<Pair<CilFile, CilStatement.AllowRule>>>()
        (oemFiles + aospFiles).forEach { file ->
            file.statements.filterIsInstance<CilStatement.AllowRule>().forEach { stmt ->
                val key = "${stmt.source}:${stmt.target}:${stmt.objectClass}:${stmt.permissions.joinToString(",")}"
                ruleMap.getOrPut(key) { mutableListOf() }.add(file to stmt)
            }
        }
        ruleMap.forEach { (key, rules) ->
            if (rules.size > 1) {
                rules.forEachIndexed { i, (file1, stmt1) ->
                    rules.drop(i + 1).forEach { (file2, stmt2) ->
                        conflicts.add(
                            Conflict(
                                type = ConflictType.DUPLICATE_RULE,
                                severity = ConflictSeverity.LOW,
                                description = "Duplicate allow rule: $key",
                                file1 = file1.absolutePath,
                                file2 = file2.absolutePath,
                                line1 = stmt1.lineNumber,
                                line2 = stmt2.lineNumber,
                                statement1 = stmt1,
                                statement2 = stmt2
                            )
                        )
                    }
                }
            }
        }

        // 4. Contradicting rules (allow in one, neverallow in another)
        val allowMap = mutableMapOf<String, MutableList<Pair<CilFile, CilStatement.AllowRule>>>()
        val neverallowMap = mutableMapOf<String, MutableList<Pair<CilFile, CilStatement.NeverAllowRule>>>()

        (oemFiles + aospFiles).forEach { file ->
            file.statements.filterIsInstance<CilStatement.AllowRule>().forEach { stmt ->
                val key = "${stmt.source}:${stmt.target}:${stmt.objectClass}"
                allowMap.getOrPut(key) { mutableListOf() }.add(file to stmt)
            }
            file.statements.filterIsInstance<CilStatement.NeverAllowRule>().forEach { stmt ->
                val key = "${stmt.source}:${stmt.target}:${stmt.objectClass}"
                neverallowMap.getOrPut(key) { mutableListOf() }.add(file to stmt)
            }
        }

        allowMap.forEach { (key, allows) ->
            neverallowMap[key]?.forEach { (file2, stmt2) ->
                allows.forEach { (file1, stmt1) ->
                    // Check if any permission in allow is denied in neverallow
                    val conflictingPerms = stmt1.permissions.intersect(stmt2.permissions)
                    if (conflictingPerms.isNotEmpty()) {
                        conflicts.add(
                            Conflict(
                                type = ConflictType.CONTRADICTING_RULES,
                                severity = ConflictSeverity.HIGH,
                                description = "Allow rule conflicts with neverallow: $key (${conflictingPerms.joinToString(",")})",
                                file1 = file1.absolutePath,
                                file2 = file2.absolutePath,
                                line1 = stmt1.lineNumber,
                                line2 = stmt2.lineNumber,
                                statement1 = stmt1,
                                statement2 = stmt2
                            )
                        )
                    }
                }
            }
        }

        // 5. Mapping version mismatches
        val aospPlatVers = pathResolver.policyVersFile(aospPath, ActiveMode.OFFLINE)
        if (aospPlatVers.exists()) {
            val actualVersion = aospPlatVers.readText().trim()
            if (actualVersion != mappingVersion) {
                conflicts.add(
                    Conflict(
                        type = ConflictType.MAPPING_VERSION_MISMATCH,
                        severity = ConflictSeverity.CRITICAL,
                        description = "Mapping version mismatch: AOSP expects '$actualVersion', project uses '$mappingVersion'",
                        file1 = aospPlatVers.absolutePath,
                        file2 = null,
                        line1 = 1,
                        line2 = null,
                        statement1 = null,
                        statement2 = null
                    )
                )
            }
        }

        // 6. Missing platform types (vendor types not in plat or mapping)
        val aospTypeNames = aospFiles.flatMap { file ->
            file.statements.filterIsInstance<CilStatement.TypeDeclaration>().map { it.name }
        }.toSet()

        val oemTypeNames = oemFiles.flatMap { file ->
            file.statements.filterIsInstance<CilStatement.TypeDeclaration>().map { it.name }
        }.toSet()

        val missingTypes = oemTypeNames.filter { it !in aospTypeNames && !it.startsWith("_") }
        missingTypes.take(50).forEach { typeName ->
            conflicts.add(
                Conflict(
                    type = ConflictType.MISSING_PLATFORM_TYPE,
                    severity = ConflictSeverity.HIGH,
                    description = "Type '$typeName' exists in OEM but not in AOSP or mapping",
                    file1 = oemFiles.firstOrNull { it.statements.any { s -> s is CilStatement.TypeDeclaration && s.name == typeName } }?.absolutePath ?: "",
                    file2 = null,
                    line1 = 0,
                    line2 = null,
                    statement1 = null,
                    statement2 = null
                )
            )
        }

        // Count by severity
        val criticalCount = conflicts.count { it.severity == ConflictSeverity.CRITICAL }
        val highCount = conflicts.count { it.severity == ConflictSeverity.HIGH }
        val mediumCount = conflicts.count { it.severity == ConflictSeverity.MEDIUM }
        val lowCount = conflicts.count { it.severity == ConflictSeverity.LOW }

        ConflictReport(
            duplicateTypes = conflicts.filter { it.type == ConflictType.DUPLICATE_TYPE },
            duplicateRules = conflicts.filter { it.type == ConflictType.DUPLICATE_RULE },
            contradictingRules = conflicts.filter { it.type == ConflictType.CONTRADICTING_RULES },
            mappingMismatches = conflicts.filter { it.type == ConflictType.MAPPING_VERSION_MISMATCH },
            missingPlatformTypes = conflicts.filter { it.type == ConflictType.MISSING_PLATFORM_TYPE },
            totalConflicts = conflicts.size,
            criticalCount = criticalCount,
            highCount = highCount,
            mediumCount = mediumCount,
            lowCount = lowCount
        )
    }

    private fun PolicyPartition.toPartition(): Partition = when (this) {
        PolicyPartition.SYSTEM -> Partition.SYSTEM
        PolicyPartition.SYSTEM_EXT -> Partition.SYSTEM_EXT
        PolicyPartition.PRODUCT -> Partition.PRODUCT
        PolicyPartition.VENDOR -> Partition.VENDOR
        PolicyPartition.ODM -> Partition.ODM
    }
}

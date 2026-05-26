package com.selinuxtoolbox.core.domain.path

import com.selinuxtoolbox.core.model.ActiveMode
import com.selinuxtoolbox.core.model.FolderHealth
import com.selinuxtoolbox.core.model.PartitionHealth
import com.selinuxtoolbox.core.model.WorkspaceValidation
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceValidator @Inject constructor(
    private val pathResolver: PathResolver
) {

    fun validate(oemPath: String, aospPath: String, workPath: String): WorkspaceValidation {
        val errors   = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!File(oemPath).exists())  errors.add("OEM folder not found: $oemPath")
        if (!File(aospPath).exists()) errors.add("AOSP folder not found: $aospPath")

        val oemPartitions  = validateTree(oemPath,  warnings)
        val aospPartitions = validateTree(aospPath, warnings)

        val mappingVersion = pathResolver.readMappingVersion(aospPath, ActiveMode.OFFLINE)
        if (!pathResolver.policyVersFile(aospPath, ActiveMode.OFFLINE).exists()) {
            warnings.add("plat_sepolicy_vers.txt not found — defaulting to $mappingVersion")
        }

        val aospPlatCil = aospPartitions.firstOrNull { it.partition == "system" }?.cilFilesFound ?: 0
        if (aospPlatCil == 0) {
            errors.add("AOSP/system/selinux/ has no .cil files — cannot proceed")
        }

        val oemSysExt = oemPartitions.firstOrNull { it.partition == "system_ext" }?.cilFilesFound ?: 0
        val oemProd   = oemPartitions.firstOrNull { it.partition == "product" }?.cilFilesFound ?: 0
        if (oemSysExt == 0 && oemProd == 0) {
            warnings.add("OEM has no system_ext or product CIL — only vendor types will be processed")
        }

        val oemOdmCil = oemPartitions.firstOrNull { it.partition == "odm" }?.cilFilesFound ?: 0
        if (oemOdmCil > 0) {
            warnings.add("OEM/odm has CIL files but ODM CIL is binary-only — only RC files scanned")
        }

        val workOutputs = File(workPath, "outputs")
        if (workOutputs.exists() && workOutputs.listFiles()?.isNotEmpty() == true) {
            warnings.add("work/outputs/ already has content — new outputs will be added alongside")
        }

        return WorkspaceValidation(
            oemPath        = oemPath,
            aospPath       = aospPath,
            workPath       = workPath,
            oemPartitions  = oemPartitions,
            aospPartitions = aospPartitions,
            mappingVersion = mappingVersion,
            isValid        = errors.isEmpty(),
            warnings       = warnings,
            errors         = errors
        )
    }

    private fun validateTree(basePath: String, warnings: MutableList<String>): List<PartitionHealth> =
        PolicyPartition.entries.map { validatePartition(basePath, it) }

    private fun validatePartition(basePath: String, partition: PolicyPartition): PartitionHealth {
        val notes = mutableListOf<String>()
        val selinuxDir = pathResolver.selinuxDir(basePath, partition, ActiveMode.OFFLINE)
        val initDir    = pathResolver.initDir(basePath, partition, ActiveMode.OFFLINE)
        val initHwDir  = pathResolver.initHwDir(basePath, partition, ActiveMode.OFFLINE)

        val selinuxExists = selinuxDir.exists() && selinuxDir.isDirectory

        val cilFiles = if (selinuxExists)
            selinuxDir.walkTopDown().filter { it.isFile && it.extension == "cil" }.count()
        else 0

        val contextFiles = if (selinuxExists)
            selinuxDir.listFiles()?.count { it.isFile && it.name.endsWith("_contexts") } ?: 0
        else 0

        val initExists = initDir.exists() && initDir.isDirectory

        val rcFiles = listOf(initDir, initHwDir)
            .filter { it.exists() && it.isDirectory }
            .sumOf { dir -> dir.listFiles()?.count { it.isFile && it.extension == "rc" } ?: 0 }

        if (partition == PolicyPartition.ODM) {
            val precompiled = File(selinuxDir, "precompiled_sepolicy")
            if (precompiled.exists()) notes.add("precompiled_sepolicy found (binary only)")
        }

        val mappingDir = pathResolver.mappingDir(basePath, partition, ActiveMode.OFFLINE)
        if (selinuxExists && mappingDir.exists()) {
            val mappingCount = mappingDir.listFiles()?.count { it.isFile && it.extension == "cil" } ?: 0
            if (mappingCount > 0) notes.add("mapping/ has $mappingCount .cil file(s) — read-only")
        }

        val health = when {
            !selinuxExists && !initExists      -> FolderHealth.MISSING
            !selinuxExists                     -> FolderHealth.PARTIAL
            cilFiles == 0 && contextFiles == 0 -> FolderHealth.EMPTY
            cilFiles > 0                       -> FolderHealth.OK
            else                               -> FolderHealth.PARTIAL
        }

        return PartitionHealth(
            partition         = partition.dirName,
            selinuxDirExists  = selinuxExists,
            cilFilesFound     = cilFiles,
            contextFilesFound = contextFiles,
            initDirExists     = initExists,
            rcFilesFound      = rcFiles,
            health            = health,
            notes             = notes
        )
    }
}

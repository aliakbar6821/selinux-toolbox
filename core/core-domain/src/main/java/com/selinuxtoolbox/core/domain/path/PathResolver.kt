package com.selinuxtoolbox.core.domain.path

import com.selinuxtoolbox.core.model.ActiveMode
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PolicyPartition(val dirName: String) {
    SYSTEM("system"),
    SYSTEM_EXT("system_ext"),
    PRODUCT("product"),
    VENDOR("vendor"),
    ODM("odm")
}

object ContextFileNames {
    val FILE_CONTEXTS = mapOf(
        PolicyPartition.SYSTEM     to "plat_file_contexts",
        PolicyPartition.SYSTEM_EXT to "system_ext_file_contexts",
        PolicyPartition.PRODUCT    to "product_file_contexts",
        PolicyPartition.VENDOR     to "vendor_file_contexts",
        PolicyPartition.ODM        to "precompiled_file_contexts"
    )
    val PROPERTY_CONTEXTS = mapOf(
        PolicyPartition.SYSTEM     to "plat_property_contexts",
        PolicyPartition.SYSTEM_EXT to "system_ext_property_contexts",
        PolicyPartition.PRODUCT    to "product_property_contexts",
        PolicyPartition.VENDOR     to "vendor_property_contexts"
    )
    val SERVICE_CONTEXTS = mapOf(
        PolicyPartition.SYSTEM     to "plat_service_contexts",
        PolicyPartition.SYSTEM_EXT to "system_ext_service_contexts",
        PolicyPartition.PRODUCT    to "product_service_contexts",
        PolicyPartition.VENDOR     to "vendor_service_contexts"
    )
    val HWSERVICE_CONTEXTS = mapOf(
        PolicyPartition.SYSTEM     to "plat_hwservice_contexts",
        PolicyPartition.SYSTEM_EXT to "system_ext_hwservice_contexts",
        PolicyPartition.PRODUCT    to "product_hwservice_contexts",
        PolicyPartition.VENDOR     to "vendor_hwservice_contexts"
    )
    val VNDSERVICE_CONTEXTS = mapOf(
        PolicyPartition.VENDOR to "vndservice_contexts"
    )
    val SEAPP_CONTEXTS = mapOf(
        PolicyPartition.SYSTEM     to "plat_seapp_contexts",
        PolicyPartition.SYSTEM_EXT to "system_ext_seapp_contexts",
        PolicyPartition.PRODUCT    to "product_seapp_contexts",
        PolicyPartition.VENDOR     to "vendor_seapp_contexts"
    )
    val KEYSTORE2_CONTEXTS = mapOf(
        PolicyPartition.SYSTEM     to "plat_keystore2_key_contexts",
        PolicyPartition.SYSTEM_EXT to "system_ext_keystore2_key_contexts",
        PolicyPartition.PRODUCT    to "product_keystore2_key_contexts",
        PolicyPartition.VENDOR     to "vendor_keystore2_key_contexts"
    )
    val MAIN_CIL = mapOf(
        PolicyPartition.SYSTEM     to "plat_sepolicy.cil",
        PolicyPartition.SYSTEM_EXT to "system_ext_sepolicy.cil",
        PolicyPartition.PRODUCT    to "product_sepolicy.cil",
        PolicyPartition.VENDOR     to "vendor_sepolicy.cil",
        PolicyPartition.ODM        to null
    )
    val VENDOR_PUB_VERSIONED = "plat_pub_versioned.cil"
    val DENIAL_METADATA      = "selinux_denial_metadata"
    val POLICY_VERS_FILE     = "plat_sepolicy_vers.txt"
}

@Singleton
class PathResolver @Inject constructor() {

    fun selinuxDir(basePath: String, partition: PolicyPartition, mode: ActiveMode): File =
        when (mode) {
            ActiveMode.OFFLINE -> File(basePath, "${partition.dirName}/selinux")
            ActiveMode.LIVE    -> File("/${partition.dirName}/etc/selinux")
        }

    fun initDir(basePath: String, partition: PolicyPartition, mode: ActiveMode): File =
        when (mode) {
            ActiveMode.OFFLINE -> File(basePath, "${partition.dirName}/init")
            ActiveMode.LIVE    -> File("/${partition.dirName}/etc/init")
        }

    fun initHwDir(basePath: String, partition: PolicyPartition, mode: ActiveMode): File =
        File(initDir(basePath, partition, mode), "hw")

    fun mainCilFile(basePath: String, partition: PolicyPartition, mode: ActiveMode): File? {
        val name = ContextFileNames.MAIN_CIL[partition] ?: return null
        return File(selinuxDir(basePath, partition, mode), name)
    }

    fun mappingDir(basePath: String, partition: PolicyPartition, mode: ActiveMode): File =
        File(selinuxDir(basePath, partition, mode), "mapping")

    fun mappingCilFile(
        basePath: String, partition: PolicyPartition, version: String, mode: ActiveMode
    ): File = File(mappingDir(basePath, partition, mode), "$version.cil")

    fun mappingCompatCilFile(
        basePath: String, partition: PolicyPartition, version: String, mode: ActiveMode
    ): File = File(mappingDir(basePath, partition, mode), "$version.compat.cil")

    fun vendorPubVersionedFile(basePath: String, mode: ActiveMode): File =
        File(selinuxDir(basePath, PolicyPartition.VENDOR, mode), ContextFileNames.VENDOR_PUB_VERSIONED)

    fun denialMetadataFile(basePath: String, mode: ActiveMode): File =
        File(selinuxDir(basePath, PolicyPartition.VENDOR, mode), ContextFileNames.DENIAL_METADATA)

    fun policyVersFile(basePath: String, mode: ActiveMode): File =
        File(selinuxDir(basePath, PolicyPartition.VENDOR, mode), ContextFileNames.POLICY_VERS_FILE)

    fun fileContexts(basePath: String, partition: PolicyPartition, mode: ActiveMode): File? {
        val name = ContextFileNames.FILE_CONTEXTS[partition] ?: return null
        return File(selinuxDir(basePath, partition, mode), name)
    }

    fun propertyContexts(basePath: String, partition: PolicyPartition, mode: ActiveMode): File? {
        val name = ContextFileNames.PROPERTY_CONTEXTS[partition] ?: return null
        return File(selinuxDir(basePath, partition, mode), name)
    }

    fun serviceContexts(basePath: String, partition: PolicyPartition, mode: ActiveMode): File? {
        val name = ContextFileNames.SERVICE_CONTEXTS[partition] ?: return null
        return File(selinuxDir(basePath, partition, mode), name)
    }

    fun hwserviceContexts(basePath: String, partition: PolicyPartition, mode: ActiveMode): File? {
        val name = ContextFileNames.HWSERVICE_CONTEXTS[partition] ?: return null
        return File(selinuxDir(basePath, partition, mode), name)
    }

    fun vndserviceContexts(basePath: String, mode: ActiveMode): File? {
        val name = ContextFileNames.VNDSERVICE_CONTEXTS[PolicyPartition.VENDOR] ?: return null
        return File(selinuxDir(basePath, PolicyPartition.VENDOR, mode), name)
    }

    fun seappContexts(basePath: String, partition: PolicyPartition, mode: ActiveMode): File? {
        val name = ContextFileNames.SEAPP_CONTEXTS[partition] ?: return null
        return File(selinuxDir(basePath, partition, mode), name)
    }

    fun allCilFiles(basePath: String, partition: PolicyPartition, mode: ActiveMode): List<File> {
        val dir = selinuxDir(basePath, partition, mode)
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "cil" }
            .toList()
            .sortedBy { it.absolutePath }
    }

    fun allRcFiles(basePath: String, partition: PolicyPartition, mode: ActiveMode): List<File> {
        val dirs = listOf(initDir(basePath, partition, mode), initHwDir(basePath, partition, mode))
        return dirs.flatMap { dir ->
            if (dir.exists()) dir.walkTopDown()
                .filter { it.isFile && it.extension == "rc" }
                .toList()
            else emptyList()
        }.sortedBy { it.absolutePath }
    }

    fun outputDir(workPath: String, operation: String): File {
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return File(workPath, "outputs/${operation}_$ts")
    }

    fun actionBackupsDir(workPath: String): File = File(workPath, "action_backups")
    fun importedLogsDir(workPath: String): File  = File(workPath, "imported_logs")
    fun projectJsonFile(workPath: String): File  = File(workPath, "project.json")

    fun readMappingVersion(aospPath: String, mode: ActiveMode): String {
        return try {
            policyVersFile(aospPath, mode).readText().trim()
        } catch (e: Exception) {
            "34.0"
        }
    }

    fun buildCilLoadOrder(
        aospPath: String,
        mappingVersion: String,
        mode: ActiveMode,
        extraFiles: List<File> = emptyList()
    ): List<File> {
        val files = mutableListOf<File>()
        fun addIfExists(f: File?) { if (f != null && f.exists()) files.add(f) }

        addIfExists(mainCilFile(aospPath, PolicyPartition.SYSTEM, mode))
        addIfExists(mappingCilFile(aospPath, PolicyPartition.SYSTEM, mappingVersion, mode))
        addIfExists(mappingCompatCilFile(aospPath, PolicyPartition.SYSTEM, mappingVersion, mode))
        addIfExists(mainCilFile(aospPath, PolicyPartition.SYSTEM_EXT, mode))
        addIfExists(mappingCilFile(aospPath, PolicyPartition.SYSTEM_EXT, mappingVersion, mode))
        addIfExists(mainCilFile(aospPath, PolicyPartition.PRODUCT, mode))
        addIfExists(vendorPubVersionedFile(aospPath, mode))
        files.addAll(extraFiles.filter { it.exists() })
        addIfExists(mainCilFile(aospPath, PolicyPartition.VENDOR, mode))

        return files
    }
}

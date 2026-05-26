package com.selinuxtoolbox.core.data.root

import com.selinuxtoolbox.core.model.Partition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// Known SELinux file paths per partition
object SelinuxPaths {

    val SYSTEM_SELINUX_DIR = "/system/etc/selinux"
    val SYSTEM_EXT_SELINUX_DIR = "/system_ext/etc/selinux"
    val PRODUCT_SELINUX_DIR = "/product/etc/selinux"
    val VENDOR_SELINUX_DIR = "/vendor/etc/selinux"
    val ODM_SELINUX_DIR = "/odm/etc/selinux"

    val INIT_DIRS = listOf(
        "/system/etc/init",
        "/system_ext/etc/init",
        "/vendor/etc/init",
        "/odm/etc/init"
    )

    // Main CIL files per partition
    val CIL_FILES = mapOf(
        Partition.SYSTEM to "$SYSTEM_SELINUX_DIR/plat_sepolicy.cil",
        Partition.SYSTEM_EXT to "$SYSTEM_EXT_SELINUX_DIR/system_ext_sepolicy.cil",
        Partition.PRODUCT to "$PRODUCT_SELINUX_DIR/product_sepolicy.cil",
        Partition.VENDOR to "$VENDOR_SELINUX_DIR/vendor_sepolicy.cil",
        Partition.ODM to "$ODM_SELINUX_DIR/odm_sepolicy.cil"
    )

    // Context files per partition
    val FILE_CONTEXTS = mapOf(
        Partition.SYSTEM to "$SYSTEM_SELINUX_DIR/plat_file_contexts",
        Partition.SYSTEM_EXT to "$SYSTEM_EXT_SELINUX_DIR/system_ext_file_contexts",
        Partition.PRODUCT to "$PRODUCT_SELINUX_DIR/product_file_contexts",
        Partition.VENDOR to "$VENDOR_SELINUX_DIR/vendor_file_contexts",
        Partition.ODM to "$ODM_SELINUX_DIR/odm_file_contexts"
    )

    val PROPERTY_CONTEXTS = mapOf(
        Partition.SYSTEM to "$SYSTEM_SELINUX_DIR/plat_property_contexts",
        Partition.VENDOR to "$VENDOR_SELINUX_DIR/vendor_property_contexts"
    )

    val SERVICE_CONTEXTS = mapOf(
        Partition.SYSTEM to "$SYSTEM_SELINUX_DIR/plat_service_contexts",
        Partition.VENDOR to "$VENDOR_SELINUX_DIR/vendor_service_contexts"
    )

    val HWSERVICE_CONTEXTS = mapOf(
        Partition.VENDOR to "$VENDOR_SELINUX_DIR/vendor_hwservice_contexts"
    )

    val VNDSERVICE_CONTEXTS = mapOf(
        Partition.VENDOR to "$VENDOR_SELINUX_DIR/vndservice_contexts"
    )

    // Vendor-specific files
    val VENDOR_PUB_VERSIONED = "$VENDOR_SELINUX_DIR/plat_pub_versioned.cil"
    val DENIAL_METADATA = "$VENDOR_SELINUX_DIR/selinux_denial_metadata"

    // Mapping dirs
    val SYSTEM_MAPPING_DIR = "$SYSTEM_SELINUX_DIR/mapping"
    val SYSTEM_EXT_MAPPING_DIR = "$SYSTEM_EXT_SELINUX_DIR/mapping"

    fun selinuxDirForPartition(partition: Partition): String = when (partition) {
        Partition.SYSTEM -> SYSTEM_SELINUX_DIR
        Partition.SYSTEM_EXT -> SYSTEM_EXT_SELINUX_DIR
        Partition.PRODUCT -> PRODUCT_SELINUX_DIR
        Partition.VENDOR -> VENDOR_SELINUX_DIR
        Partition.ODM -> ODM_SELINUX_DIR
    }
}

@Singleton
class RootFileReader @Inject constructor(
    private val rootShell: RootShell
) {

    // Read a CIL file from the live device
    suspend fun readCilFile(path: String): String? = rootShell.readFile(path)

    // Read all CIL files from a partition
    suspend fun readAllCilFiles(partition: Partition): Map<String, String> =
        withContext(Dispatchers.IO) {
            val dir = SelinuxPaths.selinuxDirForPartition(partition)
            val files = rootShell.listFilesRecursive(dir, "cil")
            val result = mutableMapOf<String, String>()
            files.forEach { path ->
                rootShell.readFile(path)?.let { content ->
                    result[path] = content
                }
            }
            result
        }

    // Read all mapping CIL files from system
    suspend fun readMappingFiles(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        listOf(
            SelinuxPaths.SYSTEM_MAPPING_DIR,
            SelinuxPaths.SYSTEM_EXT_MAPPING_DIR
        ).forEach { dir ->
            if (rootShell.dirExists(dir)) {
                rootShell.listFilesRecursive(dir, "cil").forEach { path ->
                    rootShell.readFile(path)?.let { content ->
                        result[path] = content
                    }
                }
            }
        }
        result
    }

    // Read a context file
    suspend fun readContextFile(path: String): String? = rootShell.readFile(path)

    // Read denial metadata
    suspend fun readDenialMetadata(): String? =
        rootShell.readFile(SelinuxPaths.DENIAL_METADATA)

    // Read all .rc files from init dirs
    suspend fun readAllRcFiles(dirs: List<String> = SelinuxPaths.INIT_DIRS): Map<String, String> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, String>()
            dirs.forEach { dir ->
                if (rootShell.dirExists(dir)) {
                    rootShell.listFilesRecursive(dir, "rc").forEach { path ->
                        rootShell.readFile(path)?.let { content ->
                            result[path] = content
                        }
                    }
                }
            }
            result
        }

    // Read all .rc files from a user-defined project folder (no root needed)
    suspend fun readRcFilesFromFolder(folderPath: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, String>()
            try {
                val folder = java.io.File(folderPath)
                if (folder.exists() && folder.isDirectory) {
                    folder.walkTopDown()
                        .filter { it.isFile && it.extension == "rc" }
                        .forEach { file ->
                            result[file.absolutePath] = file.readText()
                        }
                }
            } catch (e: Exception) {
                // Folder not accessible
            }
            result
        }

    // Read CIL files from a user project folder (SD card — no root needed)
    suspend fun readCilFilesFromFolder(folderPath: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, String>()
            try {
                val folder = java.io.File(folderPath)
                if (folder.exists() && folder.isDirectory) {
                    folder.walkTopDown()
                        .filter { it.isFile && it.extension == "cil" }
                        .forEach { file ->
                            result[file.absolutePath] = file.readText()
                        }
                }
            } catch (e: Exception) {
                // Folder not accessible
            }
            result
        }

    // Check which partitions are available on this device
    suspend fun getAvailablePartitions(): List<Partition> = withContext(Dispatchers.IO) {
        val available = mutableListOf<Partition>()
        Partition.entries.forEach { partition ->
            val dir = SelinuxPaths.selinuxDirForPartition(partition)
            if (rootShell.dirExists(dir)) {
                available.add(partition)
            }
        }
        available
    }

    // Get available mapping versions
    suspend fun getAvailableMappingVersions(): List<String> = withContext(Dispatchers.IO) {
        val versions = mutableListOf<String>()
        val files = rootShell.listFiles(SelinuxPaths.SYSTEM_MAPPING_DIR)
        files.forEach { file ->
            if (file.endsWith(".cil")) {
                versions.add(file.removeSuffix(".cil"))
            }
        }
        versions.sorted()
    }
}

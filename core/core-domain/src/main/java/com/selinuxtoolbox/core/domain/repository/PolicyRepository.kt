package com.selinuxtoolbox.core.domain.repository

import com.selinuxtoolbox.core.data.root.RootFileReader
import com.selinuxtoolbox.core.data.root.RootShell
import com.selinuxtoolbox.core.data.root.SelinuxPaths
import com.selinuxtoolbox.core.model.CilFile
import com.selinuxtoolbox.core.model.CilStatement
import com.selinuxtoolbox.core.model.ContextEntry
import com.selinuxtoolbox.core.model.ContextFileType
import com.selinuxtoolbox.core.model.DeviceInfo
import com.selinuxtoolbox.core.model.LoadedPolicy
import com.selinuxtoolbox.core.model.Partition
import com.selinuxtoolbox.core.data.util.FileUtil
import com.selinuxtoolbox.core.domain.parser.AvcDenialParser
import com.selinuxtoolbox.core.domain.parser.CilParser
import com.selinuxtoolbox.core.domain.parser.ContextFileParser
import com.selinuxtoolbox.core.domain.parser.DenialMetadataParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolicyRepository @Inject constructor(
    private val rootFileReader: RootFileReader,
    private val rootShell: RootShell
) {
    private val cilParser = CilParser("", Partition.SYSTEM)
    private val contextParser = ContextFileParser()
    private val metadataParser = DenialMetadataParser()

    // Load the complete policy from the live device
    suspend fun loadLivePolicy(): Result<LoadedPolicy> = withContext(Dispatchers.IO) {
        try {
            val cilFiles = mutableListOf<CilFile>()
            val contextEntries = mutableListOf<ContextEntry>()

            // Load CIL files from each partition
            val availablePartitions = rootFileReader.getAvailablePartitions()
            availablePartitions.forEach { partition ->
                val files = rootFileReader.readAllCilFiles(partition)
                files.forEach { (path, content) ->
                    val isMapping = path.contains("/mapping/")
                    val hash = FileUtil.sha256(content)
                    val parser = CilParser(path, partition)
                    val statements = parser.parse(content)
                    cilFiles.add(
                        CilFile(
                            partition = partition,
                            absolutePath = path,
                            relativePath = path.substringAfter("/etc/selinux/"),
                            isMapping = isMapping,
                            sha256 = hash,
                            statements = statements
                        )
                    )
                }
            }

            // Load context files
            contextEntries.addAll(loadContextFiles())

            // Load denial metadata
            val metadataContent = rootFileReader.readDenialMetadata() ?: ""
            val metadata = metadataParser.parse(metadataContent)

            // Get device info
            val deviceInfo = buildDeviceInfo()

            Result.success(
                LoadedPolicy(
                    cilFiles = cilFiles,
                    contextEntries = contextEntries,
                    denialMetadata = metadata,
                    loadedAt = System.currentTimeMillis(),
                    deviceInfo = deviceInfo
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Load CIL files from a user project folder on SD card
    suspend fun loadProjectPolicy(
        projectFolder: String,
        partition: Partition
    ): List<CilFile> = withContext(Dispatchers.IO) {
        val files = rootFileReader.readCilFilesFromFolder(projectFolder)
        files.map { (path, content) ->
            val isMapping = path.contains("/mapping/")
            val hash = FileUtil.sha256(content)
            val parser = CilParser(path, partition)
            val statements = parser.parse(content)
            CilFile(
                partition = partition,
                absolutePath = path,
                relativePath = path.substringAfter(projectFolder).trimStart('/'),
                isMapping = isMapping,
                sha256 = hash,
                statements = statements
            )
        }
    }

    private suspend fun loadContextFiles(): List<ContextEntry> {
        val entries = mutableListOf<ContextEntry>()

        // File contexts
        SelinuxPaths.FILE_CONTEXTS.forEach { (partition, path) ->
            rootFileReader.readContextFile(path)?.let { content ->
                entries.addAll(
                    contextParser.parse(content, path, ContextFileType.FILE, partition)
                )
            }
        }

        // Property contexts
        SelinuxPaths.PROPERTY_CONTEXTS.forEach { (partition, path) ->
            rootFileReader.readContextFile(path)?.let { content ->
                entries.addAll(
                    contextParser.parse(content, path, ContextFileType.PROPERTY, partition)
                )
            }
        }

        // Service contexts
        SelinuxPaths.SERVICE_CONTEXTS.forEach { (partition, path) ->
            rootFileReader.readContextFile(path)?.let { content ->
                entries.addAll(
                    contextParser.parse(content, path, ContextFileType.SERVICE, partition)
                )
            }
        }

        // Hwservice contexts
        SelinuxPaths.HWSERVICE_CONTEXTS.forEach { (partition, path) ->
            rootFileReader.readContextFile(path)?.let { content ->
                entries.addAll(
                    contextParser.parse(content, path, ContextFileType.HWSERVICE, partition)
                )
            }
        }

        // Vndservice contexts
        SelinuxPaths.VNDSERVICE_CONTEXTS.forEach { (partition, path) ->
            rootFileReader.readContextFile(path)?.let { content ->
                entries.addAll(
                    contextParser.parse(content, path, ContextFileType.VNDSERVICE, partition)
                )
            }
        }

        return entries
    }

    private suspend fun buildDeviceInfo(): DeviceInfo {
        val model = rootShell.getDeviceModel()
        val androidVersion = rootShell.getAndroidVersion()
        val vendorApiLevel = rootShell.getVendorApiLevel()
        val selinuxMode = rootShell.getSelinuxMode()
        val mappingVersions = rootFileReader.getAvailableMappingVersions()

        return DeviceInfo(
            model = model,
            androidVersion = androidVersion,
            vendorApiLevel = vendorApiLevel,
            selinuxMode = selinuxMode,
            availableMappingVersions = mappingVersions
        )
    }

    // Get all types declared across all CIL files
    fun getAllTypes(policy: LoadedPolicy): Set<String> {
        return policy.cilFiles
            .flatMap { it.statements }
            .filterIsInstance<CilStatement.TypeDeclaration>()
            .map { it.name }
            .toSet()
    }

    // Get all types referenced by mapping files (protected — never remove)
    fun getProtectedTypes(policy: LoadedPolicy): Set<String> {
        val protected = mutableSetOf<String>()
        policy.cilFiles
            .filter { it.isMapping }
            .flatMap { it.statements }
            .forEach { stmt ->
                when (stmt) {
                    is CilStatement.TypeDeclaration -> protected.add(stmt.name)
                    is CilStatement.TypeAttribute -> protected.add(stmt.name)
                    is CilStatement.TypeAttributeSet -> {
                        protected.add(stmt.attribute)
                        protected.addAll(stmt.members)
                    }
                    else -> {}
                }
            }
        return protected
    }

    // Get the correct CIL file loading order for secilc compilation
    fun getCompilationOrder(policy: LoadedPolicy): List<CilFile> {
        val ordered = mutableListOf<CilFile>()

        fun addIfPresent(partition: Partition, isMapping: Boolean = false) {
            policy.cilFiles
                .filter { it.partition == partition && it.isMapping == isMapping }
                .sortedBy { it.absolutePath }
                .forEach { ordered.add(it) }
        }

        // 1. Platform policy
        addIfPresent(Partition.SYSTEM, isMapping = false)
        // 2. Platform mapping
        addIfPresent(Partition.SYSTEM, isMapping = true)
        // 3. System ext
        addIfPresent(Partition.SYSTEM_EXT, isMapping = false)
        addIfPresent(Partition.SYSTEM_EXT, isMapping = true)
        // 4. Product
        addIfPresent(Partition.PRODUCT, isMapping = false)
        addIfPresent(Partition.PRODUCT, isMapping = true)
        // 5. Vendor (plat_pub_versioned first, then vendor_sepolicy)
        policy.cilFiles
            .filter { it.partition == Partition.VENDOR }
            .sortedWith(compareBy(
                { !it.absolutePath.contains("plat_pub_versioned") },
                { it.absolutePath }
            ))
            .forEach { ordered.add(it) }
        // 6. ODM
        addIfPresent(Partition.ODM, isMapping = false)

        return ordered
    }
}

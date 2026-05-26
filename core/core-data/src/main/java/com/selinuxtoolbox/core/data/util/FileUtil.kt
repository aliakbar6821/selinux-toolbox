package com.selinuxtoolbox.core.data.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtil {

    // ── Hashing ───────────────────────────────────────────────────────────────

    suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(content.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ── Zip ───────────────────────────────────────────────────────────────────

    suspend fun createZip(
        files: List<File>,
        outputZip: File,
        baseDir: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            outputZip.parentFile?.mkdirs()
            ZipOutputStream(outputZip.outputStream().buffered()).use { zos ->
                files.forEach { file ->
                    if (file.exists() && file.isFile) {
                        val entryName = if (baseDir.isNotEmpty()) {
                            file.absolutePath
                                .removePrefix(baseDir)
                                .trimStart('/', '\\')
                        } else {
                            file.name
                        }
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun extractZip(zipFile: File, targetDir: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                targetDir.mkdirs()
                ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(targetDir, entry.name)
                        val canonicalTarget = targetDir.canonicalPath
                        val canonicalOut = outFile.canonicalPath
                        if (!canonicalOut.startsWith(canonicalTarget)) {
                            entry = zis.nextEntry
                            continue
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { zis.copyTo(it) }
                        }
                        entry = zis.nextEntry
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    // ── Directory helpers ─────────────────────────────────────────────────────

    fun ensureOutputDirs(basePath: String): OutputDirs {
        val base     = File(basePath)
        val projects = File(base, "projects")
        val exports  = File(base, "exports")
        projects.mkdirs()
        exports.mkdirs()
        return OutputDirs(base, projects, exports)
    }

    fun ensureProjectDirs(projectsBase: File, projectName: String): ProjectDirs {
        val project        = File(projectsBase, projectName)
        val originalBackup = File(project, "original_backup")
        val output         = File(project, "output")
        val actionBackups  = File(project, "action_backups")
        val reports        = File(project, "reports")
        val importedLogs   = File(project, "imported_logs")
        listOf(project, originalBackup, output, actionBackups, reports, importedLogs)
            .forEach { it.mkdirs() }
        return ProjectDirs(project, originalBackup, output, actionBackups, reports, importedLogs)
    }

    /**
     * Create the full OFFLINE workspace scaffold under [workspaceRoot]:
     *
     *   <workspaceRoot>/
     *     OEM/
     *       system/selinux/    system/init/
     *       system_ext/selinux/  system_ext/init/
     *       product/selinux/   product/init/
     *       vendor/selinux/    vendor/init/
     *       odm/selinux/       odm/init/
     *     AOSP/   (same partition structure)
     *     work/
     *       outputs/
     *       action_backups/
     *       imported_logs/
     *     logs/               ← user drops raw boot logs here
     *
     * Returns [WorkspaceDirs] with the root paths for OEM, AOSP, work, logs.
     */
    fun ensureWorkspaceDirs(workspaceRoot: File): WorkspaceDirs {
        val partitions = listOf("system", "system_ext", "product", "vendor", "odm")

        fun scaffold(base: File) {
            partitions.forEach { part ->
                File(base, "$part/selinux").mkdirs()
                File(base, "$part/init").mkdirs()
            }
        }

        val oemDir  = File(workspaceRoot, "OEM").also  { scaffold(it) }
        val aospDir = File(workspaceRoot, "AOSP").also { scaffold(it) }
        val workDir = File(workspaceRoot, "work").also {
            File(it, "outputs").mkdirs()
            File(it, "action_backups").mkdirs()
            File(it, "imported_logs").mkdirs()
        }
        val logsDir = File(workspaceRoot, "logs").also { it.mkdirs() }

        return WorkspaceDirs(
            root = workspaceRoot,
            oem  = oemDir,
            aosp = aospDir,
            work = workDir,
            logs = logsDir
        )
    }

    // ── Common ancestor ───────────────────────────────────────────────────────

    fun commonAncestor(files: List<File>): File? {
        if (files.isEmpty()) return null
        if (files.size == 1) return files[0].parentFile
        var ancestor = files[0].parentFile
        for (file in files.drop(1)) {
            ancestor = commonAncestor(ancestor ?: return null, file.parentFile ?: return null)
        }
        return ancestor
    }

    private fun commonAncestor(a: File, b: File): File {
        val aParts = a.canonicalPath.split(File.separator)
        val bParts = b.canonicalPath.split(File.separator)
        val common = aParts.zip(bParts)
            .takeWhile { (x, y) -> x == y }
            .map { it.first }
        return File(common.joinToString(File.separator))
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class OutputDirs(
        val base: File,
        val projects: File,
        val exports: File
    )

    data class ProjectDirs(
        val root: File,
        val originalBackup: File,
        val output: File,
        val actionBackups: File,
        val reports: File,
        val importedLogs: File
    )

    data class WorkspaceDirs(
        val root: File,
        val oem: File,
        val aosp: File,
        val work: File,
        val logs: File
    )
}

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

    // Compute SHA256 of a file
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

    // Compute SHA256 of a string
    fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(content.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // Create a zip from a list of files
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
                            file.absolutePath.removePrefix(baseDir).trimStart('/')
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

    // Extract a zip to a directory
    suspend fun extractZip(zipFile: File, targetDir: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                targetDir.mkdirs()
                ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(targetDir, entry.name)
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

    // Ensure the SELinuxToolbox output directory structure exists
    fun ensureOutputDirs(basePath: String): OutputDirs {
        val base = File(basePath)
        val projects = File(base, "projects")
        val exports = File(base, "exports")
        projects.mkdirs()
        exports.mkdirs()
        return OutputDirs(base, projects, exports)
    }

    // Create project-specific directories
    fun ensureProjectDirs(projectsBase: File, projectName: String): ProjectDirs {
        val project = File(projectsBase, projectName)
        val originalBackup = File(project, "original_backup")
        val output = File(project, "output")
        val actionBackups = File(project, "action_backups")
        val reports = File(project, "reports")
        val importedLogs = File(project, "imported_logs")

        listOf(project, originalBackup, output, actionBackups, reports, importedLogs)
            .forEach { it.mkdirs() }

        return ProjectDirs(project, originalBackup, output, actionBackups, reports, importedLogs)
    }

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
}

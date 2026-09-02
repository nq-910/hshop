package me.erista.hshop.thor.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

object StorageUtils {

    fun getAbsolutePathFromTreeUri(context: Context, uri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            val relativePath = if (split.size > 1) split[1] else ""

            if ("primary".equals(type, ignoreCase = true)) {
                if (relativePath.isNotEmpty()) {
                    File(Environment.getExternalStorageDirectory(), relativePath).absolutePath
                } else {
                    Environment.getExternalStorageDirectory().absolutePath
                }
            } else {
                // Secondary storage / SD Card (e.g. 1234-5678)
                val base = File("/storage/$type")
                if (base.exists() && relativePath.isNotEmpty()) {
                    File(base, relativePath).absolutePath
                } else if (relativePath.isNotEmpty()) {
                    "/storage/$type/$relativePath"
                } else {
                    "/storage/$type"
                }
            }
        } catch (e: Exception) {
            uri.path ?: Environment.getExternalStorageDirectory().absolutePath
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val formatted = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", formatted, units[digitGroups])
    }

    fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) size += file.length()
            }
        } catch (_: Exception) {}
        return size
    }

    fun clearDirectory(dir: File): Boolean {
        return try {
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.deleteRecursively() }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getUsableSpace(path: String): Long {
        return try {
            val f = File(path)
            if (f.exists()) f.usableSpace else f.parentFile?.usableSpace ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}

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
}

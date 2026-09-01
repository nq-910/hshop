package me.erista.hshop.thor.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object GameLauncher {

    /**
     * Opens the downloaded ROM (.cia, .3ds, .cci, .zcci) with Lime3DS, Azahar, or other installed 3DS apps.
     */
    fun launchGame(context: Context, filePath: String, onBeforeLaunch: (() -> Unit)? = null) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            onBeforeLaunch?.invoke()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if Lime3DS is installed to launch directly without extra prompt
            val limePackage = "io.github.lime3ds.android"
            val pm = context.packageManager
            val limeIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/octet-stream")
                setPackage(limePackage)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (limeIntent.resolveActivity(pm) != null) {
                context.startActivity(limeIntent)
            } else {
                val chooser = Intent.createChooser(intent, "Open ${file.name} with:")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open app: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

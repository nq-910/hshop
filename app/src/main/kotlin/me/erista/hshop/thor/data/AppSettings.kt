package me.erista.hshop.thor.data

import android.os.Environment
import androidx.compose.ui.graphics.Color
import java.io.File

enum class AppTheme(val displayName: String, val primaryColor: Color, val accentColor: Color) {
    THOR_AMOLED(
        displayName = "Thor AMOLED Green",
        primaryColor = Color(0xFF18FF00),
        accentColor = Color(0xFF00E5FF)
    ),
    CYBERPUNK_NEON(
        displayName = "Cyberpunk Neon",
        primaryColor = Color(0xFFFF007F),
        accentColor = Color(0xFF00F0FF)
    ),
    NINTENDO_RED(
        displayName = "Nintendo Red",
        primaryColor = Color(0xFFE60012),
        accentColor = Color(0xFFFFFFFF)
    ),
    CITRA_YELLOW(
        displayName = "Citra Yellow",
        primaryColor = Color(0xFFFFCC00),
        accentColor = Color(0xFFFF8800)
    )
}

data class AppSettings(
    val downloadPath: String = File(Environment.getExternalStorageDirectory(), "ROMs/3DS").absolutePath,
    val updateDlcPath: String = File(Environment.getExternalStorageDirectory(), "ROMs/3DS/Updates_DLC").absolutePath,
    val theme: AppTheme = AppTheme.THOR_AMOLED,
    val downloadOverWifiOnly: Boolean = false,
    val autoCreateFolders: Boolean = true,
    val autoRemoveDownloadedCia: Boolean = false,
    val autoConvertTo3ds: Boolean = true,
    val autoCompressToZcci: Boolean = false,
    val autoDownloadRelatedContent: Boolean = true,
    val allowedRegions: Set<String> = emptySet() // Empty means all regions enabled
)

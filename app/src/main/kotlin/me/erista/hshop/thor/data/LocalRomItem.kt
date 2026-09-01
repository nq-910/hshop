package me.erista.hshop.thor.data

import java.io.File

enum class LocalFileType(val displayName: String, val extension: String) {
    CCI("Decrypted .CCI", "cci"),
    ZCCI("Compressed .ZCCI", "zcci"),
    THREE_DS("Decrypted .3DS", "3ds"),
    CIA("Raw .CIA Package", "cia")
}

data class LocalRomItem(
    val file: File,
    val name: String,
    val productCode: String,
    val fileType: LocalFileType,
    val sizeBytes: Long,
    val sizeString: String,
    val lastModified: Long,
    val isDecrypted: Boolean,
    val isUpdateOrDlc: Boolean
)

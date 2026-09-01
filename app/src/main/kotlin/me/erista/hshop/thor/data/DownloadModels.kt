package me.erista.hshop.thor.data

enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    CONVERTING,
    COMPLETED,
    PAUSED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val id: String,
    val titleName: String,
    val productCode: String,
    val downloadUrl: String,
    val targetFilePath: String,
    val convertedFilePath: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val progress: Float = 0f,
    val errorMessage: String? = null
) {
    val progressPercent: Int
        get() = (progress * 100).toInt().coerceIn(0, 100)

    val speedString: String
        get() {
            val speedMb = speedBytesPerSec / (1024.0 * 1024.0)
            return if (speedMb >= 1.0) "%.1f MB/s".format(speedMb) else "%.0f KB/s".format(speedBytesPerSec / 1024.0)
        }

    val sizeString: String
        get() {
            val downloadedMb = bytesDownloaded / (1024.0 * 1024.0)
            val totalMb = totalBytes / (1024.0 * 1024.0)
            return if (totalBytes > 0) {
                "%.1f MB / %.1f MB".format(downloadedMb, totalMb)
            } else {
                "%.1f MB".format(downloadedMb)
            }
        }
}
